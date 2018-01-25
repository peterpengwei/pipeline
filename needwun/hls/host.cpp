#include <boost/thread/thread.hpp>
#include <boost/lockfree/spsc_queue.hpp>
#include <iostream>
#include <stdio.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <cctype>
#include <boost/atomic.hpp>
#include <signal.h>
#include <errno.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <unistd.h>
#include <assert.h>
#include <stdbool.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <CL/opencl.h>

#include "my_timer.h"

#define PORT 6070
#define QUEUE_CAPACITY 8
#define NUM_BUFFERS (((QUEUE_CAPACITY)*(2)) + 2)

int TILE_SIZE;

cl_mem contentsA[NUM_BUFFERS];
cl_mem contentsB[NUM_BUFFERS];
cl_mem resultsA[NUM_BUFFERS];
cl_mem resultsB[NUM_BUFFERS];
int buf_ptr;

boost::lockfree::spsc_queue<int, boost::lockfree::capacity<QUEUE_CAPACITY> > input_queue;
boost::lockfree::spsc_queue<int, boost::lockfree::capacity<QUEUE_CAPACITY> > output_queue;

timespec gather_time, scatter_time, compute_time, accept_time, connect_time, input_full_time, output_full_time, input_empty_time, output_empty_time;
       
cl_context context;                 // compute context
cl_command_queue commands;          // compute command queue
cl_program program;                 // compute program
cl_kernel kernel;                   // compute kernel

void signal_callback_handler(int signum) {
    std::cerr << "Caught signal " << signum << std::endl;
    printTimeSpec(input_full_time, "input_full_time");
    printTimeSpec(accept_time, "accept_time");
    printTimeSpec(gather_time, "gather_time");

    printTimeSpec(input_empty_time, "input_empty_time");
    printTimeSpec(compute_time, "compute_time");
    printTimeSpec(output_full_time, "output_full_time");

    printTimeSpec(output_empty_time, "output_empty_time");
    printTimeSpec(connect_time, "connect_time");
    printTimeSpec(scatter_time, "scatter_time");

    for (int i=0; i<NUM_BUFFERS; i++) {
	clReleaseMemObject(contentsA[i]);
	clReleaseMemObject(contentsB[i]);
	clReleaseMemObject(resultsA[i]);
	clReleaseMemObject(resultsB[i]);
    }

    clReleaseProgram(program);
    clReleaseKernel(kernel);
    clReleaseCommandQueue(commands);
    clReleaseContext(context);

    exit(signum);
}

int
load_file_to_memory(const char *filename, char **result)
{ 
  size_t size = 0;
  FILE *f = fopen(filename, "rb");
  if (f == NULL) 
  { 
    *result = NULL;
    return -1; // -1 means file opening fail 
  } 
  fseek(f, 0, SEEK_END);
  size = ftell(f);
  fseek(f, 0, SEEK_SET);
  *result = (char *)malloc(size+1);
  if (size != fread(*result, sizeof(char), size, f)) 
  { 
    free(*result);
    return -2; // -2 means file reading fail 
  } 
  fclose(f);
  (*result)[size] = 0;
  return size;
}

void gather(void) {

    int server_fd;
    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        std::cerr << "Socket failed" << std::endl;
        exit(EXIT_FAILURE);
    }

    int opt = 1;
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        std::cerr << "Setsockopt failed" << std::endl;
    }

    sockaddr_in address;
    bzero(&address, sizeof(address));
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY;
    address.sin_port = htons(PORT);
    if (bind(server_fd, (struct sockaddr *) &address, sizeof(address)) < 0) {
        std::cerr << "Bind failed" << std::endl;
        exit(EXIT_FAILURE);
    }

    if (listen(server_fd, 64) < 0) {
        std::cerr << "Listen failed" << std::endl;
        exit(EXIT_FAILURE);
    }

    int addrlen = sizeof(address);
    //int num_gather = 0;
    while (true) {
	timespec start_time = tic();
	while (input_queue.write_available() <= 0) ;
	input_full_time = sum(input_full_time, toc(&start_time));

	start_time = tic();
        int instance = accept(server_fd, (struct sockaddr *) &address, (socklen_t *) &addrlen);
	accept_time = sum(accept_time, toc(&start_time));

	start_time = tic();
        if (instance < 0) {
            std::cerr << "Accept failed" << std::endl;
        }
        else {
            char* bufferA = (char *)clEnqueueMapBuffer(commands, contentsA[buf_ptr], CL_TRUE, CL_MAP_WRITE, 0, TILE_SIZE/2, 0, NULL, NULL, NULL);
	    int total_size = TILE_SIZE/2;
	    int n;
	    char* p = bufferA;
            while ((n = read(instance, p, total_size)) > 0) {
	        if (n >= total_size) break;
		p += n;
		total_size -= n;
	    }
            char* bufferB = (char *)clEnqueueMapBuffer(commands, contentsB[buf_ptr], CL_TRUE, CL_MAP_WRITE, 0, TILE_SIZE/2, 0, NULL, NULL, NULL);
	    total_size = TILE_SIZE/2;
	    p = bufferB;
            while ((n = read(instance, p, total_size)) > 0) {
	        if (n >= total_size) break;
		p += n;
		total_size -= n;
	    }
	    close(instance);
            clEnqueueUnmapMemObject(commands, contentsA[buf_ptr], bufferA, 0, NULL, NULL);
            clEnqueueUnmapMemObject(commands, contentsB[buf_ptr], bufferB, 0, NULL, NULL);
            input_queue.push(buf_ptr);
	    buf_ptr = (buf_ptr + 1) % NUM_BUFFERS;
	    //num_gather++;
	    //if (num_gather % 10 == 0)
	    //    std::cout << "Received " << num_gather << " requests" << std::endl;
        }
	gather_time = sum(gather_time, toc(&start_time));
    }
}

void compute(void) {
    //int num_compute = 0;
    while (true) {
	int cur_idx;
	int NUM_JOBS = TILE_SIZE/256;
	timespec start_time = tic();
        while (!input_queue.pop(cur_idx)) ;
	input_empty_time = sum(input_empty_time, toc(&start_time));

	start_time = tic();
        int err = 0;
        // Set the arguments to our compute kernel
        //
        err  = clSetKernelArg(kernel, 0, sizeof(cl_mem), &(contentsA[cur_idx]));
        err |= clSetKernelArg(kernel, 1, sizeof(cl_mem), &(contentsB[cur_idx]));
        err |= clSetKernelArg(kernel, 2, sizeof(cl_mem), &(resultsA[cur_idx]));
        err |= clSetKernelArg(kernel, 3, sizeof(cl_mem), &(resultsB[cur_idx]));
        err |= clSetKernelArg(kernel, 4, sizeof(int), &NUM_JOBS);
        if (err != CL_SUCCESS)
        {
          printf("Error: Failed to set kernel arguments! %d\n", err);
          printf("Test failed\n");
	  exit(1);
        }
      
        // Execute the kernel over the entire range of our 1d input data set
        // using the maximum number of work group items for this device
        //
        err = clEnqueueTask(commands, kernel, 0, NULL, NULL);
        if (err)
        {
          printf("Error: Failed to execute kernel! %d\n", err);
          printf("Test failed\n");
	  exit(1);
        }
	clFinish(commands);
	compute_time = sum(compute_time, toc(&start_time));
  
	start_time = tic();
        while (!output_queue.push(cur_idx)) ;
	output_full_time = sum(output_full_time, toc(&start_time));
	//num_compute++;
	//if (num_compute % 10 == 0)
	//    std::cout << "Processed " << num_compute << " requests" << std::endl;
    }
}

void scatter(void) {
    sockaddr_in serv_addr;
    bzero(&serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(9520);
    if (inet_pton(AF_INET, "127.0.0.1", &(serv_addr.sin_addr)) <= 0) {
        std::cerr << "Inet_pton failed" << std::endl;
        exit(EXIT_FAILURE);
    }

    //int num_scatter = 0;
    while (true) {
	timespec start_time = tic();
	while (output_queue.read_available() <= 0) ;
	output_empty_time = sum(output_empty_time, toc(&start_time));

	start_time = tic();
	int cur_idx;
        output_queue.pop(cur_idx);
        char* bufferA = (char *)clEnqueueMapBuffer(commands, resultsA[cur_idx], CL_TRUE, CL_MAP_READ, 0, TILE_SIZE, 0, NULL, NULL, NULL);
        char* bufferB = (char *)clEnqueueMapBuffer(commands, resultsB[cur_idx], CL_TRUE, CL_MAP_READ, 0, TILE_SIZE, 0, NULL, NULL, NULL);
        int sock;
        if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
            std::cerr << "Socket failed" << std::endl;
            exit(EXIT_FAILURE);
        }
    
        while (connect(sock, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) ;
	connect_time = sum(connect_time, toc(&start_time));

	start_time = tic();
	char* p = bufferA;
	int total_size = TILE_SIZE;
	int n;
        while ((n = write(sock, bufferA, total_size)) > 0) {
	    if (n >= total_size) break;
	    p += n;
	    total_size -= n;
	}
	p = bufferB;
	total_size = TILE_SIZE;
        while ((n = write(sock, bufferB, total_size)) > 0) {
	    if (n >= total_size) break;
	    p += n;
	    total_size -= n;
	}
	close(sock);
        clEnqueueUnmapMemObject(commands, resultsA[cur_idx], bufferA, 0, NULL, NULL);
        clEnqueueUnmapMemObject(commands, resultsB[cur_idx], bufferB, 0, NULL, NULL);
	scatter_time = sum(scatter_time, toc(&start_time));
	//num_scatter++;
	//if (num_scatter % 10 == 0)
	//    std::cout << "Scatter " << num_scatter << " requests" << std::endl;
    }
}

int main(int argc, char* argv[]) {

    if (argc != 3) {
	std::cerr << "Wrong command-line format" << std::endl;
	exit(1);
    }

    signal(SIGINT, signal_callback_handler);
    signal(SIGTERM, signal_callback_handler);

    TILE_SIZE = atoi(argv[2]);

    int err;                            // error code returned from api calls
     
    char cl_platform_vendor[1001];
    char cl_platform_name[1001];

    cl_platform_id platform_id;         // platform id
    cl_device_id device_id;             // compute device id 
     
    // Connect to first platform
    //
    err = clGetPlatformIDs(1,&platform_id,NULL);
    if (err != CL_SUCCESS)
    {
      printf("Error: Failed to find an OpenCL platform!\n");
      printf("Test failed\n");
      return EXIT_FAILURE;
    }
    err = clGetPlatformInfo(platform_id,CL_PLATFORM_VENDOR,1000,(void *)cl_platform_vendor,NULL);
    if (err != CL_SUCCESS)
    {
      printf("Error: clGetPlatformInfo(CL_PLATFORM_VENDOR) failed!\n");
      printf("Test failed\n");
      return EXIT_FAILURE;
    }
    printf("CL_PLATFORM_VENDOR %s\n",cl_platform_vendor);
    err = clGetPlatformInfo(platform_id,CL_PLATFORM_NAME,1000,(void *)cl_platform_name,NULL);
    if (err != CL_SUCCESS)
    {
      printf("Error: clGetPlatformInfo(CL_PLATFORM_NAME) failed!\n");
      printf("Test failed\n");
      return EXIT_FAILURE;
    }
    printf("CL_PLATFORM_NAME %s\n",cl_platform_name);
   
    // Connect to a compute device
    //
    int fpga = 0;
#if defined (FPGA_DEVICE)
    fpga = 1;
#endif
    err = clGetDeviceIDs(platform_id, fpga ? CL_DEVICE_TYPE_ACCELERATOR : CL_DEVICE_TYPE_CPU,
                         1, &device_id, NULL);
    if (err != CL_SUCCESS)
    {
      printf("Error: Failed to create a device group!\n");
      printf("Test failed\n");
      return EXIT_FAILURE;
    }
    
    // Create a compute context 
    //
    context = clCreateContext(0, 1, &device_id, NULL, NULL, &err);
    if (!context)
    {
      printf("Error: Failed to create a compute context!\n");
      printf("Test failed\n");
      return EXIT_FAILURE;
    }
  
    // Create a command commands
    //
    commands = clCreateCommandQueue(context, device_id, 0, &err);
    if (!commands)
    {
      printf("Error: Failed to create a command commands!\n");
      printf("Error: code %i\n",err);
      printf("Test failed\n");
      return EXIT_FAILURE;
    }
  
    int status;
  
    // Create Program Objects
    //
    
    // Load binary from disk
    unsigned char *kernelbinary;
    char *xclbin=argv[1];
    printf("loading %s\n", xclbin);
    int n_i = load_file_to_memory(xclbin, (char **) &kernelbinary);
    if (n_i < 0) {
      printf("failed to load kernel from xclbin: %s\n", xclbin);
      printf("Test failed\n");
      return EXIT_FAILURE;
    }
    size_t n = n_i;
    // Create the compute program from offline
    program = clCreateProgramWithBinary(context, 1, &device_id, &n,
                                        (const unsigned char **) &kernelbinary, &status, &err);
    if ((!program) || (err!=CL_SUCCESS)) {
      printf("Error: Failed to create compute program from binary %d!\n", err);
      printf("Test failed\n");
      return EXIT_FAILURE;
    }
  
    // Build the program executable
    //
    err = clBuildProgram(program, 0, NULL, NULL, NULL, NULL);
    if (err != CL_SUCCESS)
    {
      size_t len;
      char buffer[2048];
  
      printf("Error: Failed to build program executable!\n");
      clGetProgramBuildInfo(program, device_id, CL_PROGRAM_BUILD_LOG, sizeof(buffer), buffer, &len);
      printf("%s\n", buffer);
      printf("Test failed\n");
      return EXIT_FAILURE;
    }
  
    // Create the compute kernel in the program we wish to run
    //
    kernel = clCreateKernel(program, "workload", &err);
    if (!kernel || err != CL_SUCCESS)
    {
      printf("Error: Failed to create compute kernel!\n");
      printf("Test failed\n");
      return EXIT_FAILURE;
    }

    int i;
    for (i=0; i<NUM_BUFFERS; i++) {
      // Create the device buffer for our calculation
      //
      contentsA[i] = clCreateBuffer(context, CL_MEM_READ_WRITE, TILE_SIZE/2, NULL, NULL);
      if (!contentsA[i])
      {
        printf("Error: Failed to allocate device memory!\n");
        printf("Test failed\n");
        return EXIT_FAILURE;
      }    
      resultsA[i] = clCreateBuffer(context, CL_MEM_READ_WRITE, TILE_SIZE, NULL, NULL);
      if (!resultsA[i])
      {
        printf("Error: Failed to allocate device memory!\n");
        printf("Test failed\n");
        return EXIT_FAILURE;
      }    
      contentsB[i] = clCreateBuffer(context, CL_MEM_READ_WRITE, TILE_SIZE/2, NULL, NULL);
      if (!contentsB[i])
      {
        printf("Error: Failed to allocate device memory!\n");
        printf("Test failed\n");
        return EXIT_FAILURE;
      }    
      resultsB[i] = clCreateBuffer(context, CL_MEM_READ_WRITE, TILE_SIZE, NULL, NULL);
      if (!resultsB[i])
      {
        printf("Error: Failed to allocate device memory!\n");
        printf("Test failed\n");
        return EXIT_FAILURE;
      }    
    }

    buf_ptr = 0;

    gather_time.tv_nsec = 0;
    gather_time.tv_sec = 0;
    accept_time.tv_nsec = 0;
    accept_time.tv_sec = 0;
    input_full_time.tv_nsec = 0;
    input_full_time.tv_sec = 0;

    output_empty_time.tv_nsec = 0;
    output_empty_time.tv_sec = 0;
    connect_time.tv_nsec = 0;
    connect_time.tv_sec = 0;
    scatter_time.tv_nsec = 0;
    scatter_time.tv_sec = 0;

    output_full_time.tv_nsec = 0;
    output_full_time.tv_sec = 0;
    input_empty_time.tv_nsec = 0;
    input_empty_time.tv_sec = 0;
    compute_time.tv_nsec = 0;
    compute_time.tv_sec = 0;

    std::cout << "Start service:" << std::endl;
    boost::thread gather_thread(gather);
    boost::thread compute_thread(compute);
    boost::thread scatter_thread(scatter);

    gather_thread.join();
    compute_thread.join();
    scatter_thread.join();

    printTimeSpec(input_full_time, "input_full_time");
    printTimeSpec(accept_time, "accept_time");
    printTimeSpec(gather_time, "gather_time");

    printTimeSpec(input_empty_time, "input_empty_time");
    printTimeSpec(compute_time, "compute_time");
    printTimeSpec(output_full_time, "output_full_time");

    printTimeSpec(output_empty_time, "output_empty_time");
    printTimeSpec(connect_time, "connect_time");
    printTimeSpec(scatter_time, "scatter_time");

    for (i=0; i<NUM_BUFFERS; i++) {
	clReleaseMemObject(contentsA[i]);
	clReleaseMemObject(contentsB[i]);
	clReleaseMemObject(resultsA[i]);
	clReleaseMemObject(resultsB[i]);
    }

    clReleaseProgram(program);
    clReleaseKernel(kernel);
    clReleaseCommandQueue(commands);
    clReleaseContext(context);

    return 0;
}
