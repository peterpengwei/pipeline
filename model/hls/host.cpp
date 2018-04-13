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
#define QUEUE_CAPACITY 4
#define NUM_BUFFERS (((QUEUE_CAPACITY)*(2)) + 2)

int TILE_SIZE;
int throughput;
int combine_rate;
int meaningful_kernel;

timespec micro_sec;

cl_mem contents[NUM_BUFFERS];
cl_mem results[NUM_BUFFERS];
int buf_ptr;

boost::lockfree::spsc_queue<int, boost::lockfree::capacity<QUEUE_CAPACITY> > input_queue;
boost::lockfree::spsc_queue<int, boost::lockfree::capacity<QUEUE_CAPACITY> > output_queue;

timespec gOpenCL_time, sOpenCL_time, gather_time, scatter_time, compute_time, accept_time, connect_time, input_full_time, output_full_time, input_empty_time, output_empty_time;
       
cl_context context;                 // compute context
cl_command_queue commands;          // compute command queue
cl_program program;                 // compute program
cl_kernel kernel;                   // compute kernel

void signal_callback_handler(int signum) {
    std::cerr << "Caught signal " << signum << std::endl;
    printTimeSpec(compute_time, "compute_time");
    printTimeSpec(gather_time, "gather_time");
    printTimeSpec(gOpenCL_time, "gOpenCL_time");
    printTimeSpec(scatter_time, "scatter_time");
    printTimeSpec(sOpenCL_time, "sOpenCL_time");

    printTimeSpec(accept_time, "accept_time");
    printTimeSpec(connect_time, "connect_time");

    printTimeSpec(input_full_time, "input_full_time");
    printTimeSpec(input_empty_time, "input_empty_time");
    printTimeSpec(output_full_time, "output_full_time");
    printTimeSpec(output_empty_time, "output_empty_time");

    for (int i=0; i<NUM_BUFFERS; i++) {
	clReleaseMemObject(contents[i]);
	clReleaseMemObject(results[i]);
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

    if (listen(server_fd, 128) < 0) {
        std::cerr << "Listen failed" << std::endl;
        exit(EXIT_FAILURE);
    }

    int piece_idx = 0;
    char* buffer = NULL;

    int addrlen = sizeof(address);
    int num_gather = 0;
    while (true) {
	timespec start_time = tic();
	if (piece_idx == combine_rate - 1) {
	    while (input_queue.write_available() <= 0) ;
	    //while (input_queue.write_available() <= 0) nanosleep(&micro_sec, &micro_sec);
	}
	input_full_time = sum(input_full_time, toc(&start_time));

	start_time = tic();
        int instance = accept(server_fd, (struct sockaddr *) &address, (socklen_t *) &addrlen);
	accept_time = sum(accept_time, toc(&start_time));

        if (instance < 0) {
            std::cerr << "Accept failed" << std::endl;
        }
        else {
	    //std::cout << "gather: piece_idx = " << piece_idx << ", buffer_idx = " << buf_ptr << std::endl;
	    start_time = tic();
	    if (piece_idx == 0) {
	        //std::cout << "scatter: mapping: piece_idx = " << piece_idx << ", buffer_idx = " << buf_ptr << std::endl;
                buffer = (char *)clEnqueueMapBuffer(commands, contents[buf_ptr], CL_TRUE, CL_MAP_WRITE, 0, TILE_SIZE*combine_rate, 0, NULL, NULL, NULL);
	    }
	    gOpenCL_time = sum(gOpenCL_time, toc(&start_time));

	    start_time = tic();
	    int total_size = TILE_SIZE;
	    int n;
	    char* p = buffer + TILE_SIZE*piece_idx;
            while ((n = read(instance, p, total_size)) > 0) {
	        if (n >= total_size) break;
		p += n;
		total_size -= n;
	    }
	    close(instance);
	    gather_time = sum(gather_time, toc(&start_time));

	    start_time = tic();
	    if (piece_idx == combine_rate - 1) {
	        //std::cout << "scatter: releasing: piece_idx = " << piece_idx << ", buffer_idx = " << buf_ptr << std::endl;
		cl_event event;
                clEnqueueUnmapMemObject(commands, contents[buf_ptr], buffer, 0, NULL, &event);
		clWaitForEvents(1, &event);
		//std::cout << "scatter: released!" << std::endl;
	    }
	    gOpenCL_time = sum(gOpenCL_time, toc(&start_time));

	    start_time = tic();
	    if (piece_idx == combine_rate - 1) {
                input_queue.push(buf_ptr);
	        buf_ptr = (buf_ptr + 1) % NUM_BUFFERS;
	    }
	    piece_idx = (piece_idx + 1) % combine_rate;
	    gather_time = sum(gather_time, toc(&start_time));

	    num_gather++;
	    //if (num_gather % 10 == 0)
	        //std::cout << "Received " << num_gather << " requests" << std::endl;
        }
    }
}

void compute(void) {
    //int num_compute = 0;
    while (true) {
	int cur_idx;
	timespec start_time = tic();
        while (!input_queue.pop(cur_idx)) ;
        //while (!input_queue.pop(cur_idx)) nanosleep(&micro_sec, &micro_sec);
	input_empty_time = sum(input_empty_time, toc(&start_time));

	start_time = tic();
	int buffer_size = TILE_SIZE * combine_rate;
        int err = 0;
        // Set the arguments to our compute kernel
        //
        err  = clSetKernelArg(kernel, 0, sizeof(cl_mem), &(contents[cur_idx]));
        err |= clSetKernelArg(kernel, 1, sizeof(cl_mem), &(results[cur_idx]));
        err |= clSetKernelArg(kernel, 2, sizeof(int), &buffer_size);
        err |= clSetKernelArg(kernel, 3, sizeof(int), &meaningful_kernel);
        if (err != CL_SUCCESS)
        {
          printf("Error: Failed to set kernel arguments! %d\n", err);
          printf("Test failed\n");
	  exit(1);
        }
      
        // Execute the kernel over the entire range of our 1d input data set
        // using the maximum number of work group items for this device
        //
	cl_event event;
        err = clEnqueueTask(commands, kernel, 0, NULL, &event);
        if (err)
        {
          printf("Error: Failed to execute kernel! %d\n", err);
          printf("Test failed\n");
	  exit(1);
        }
	//usleep(buffer_size/throughput);
	clWaitForEvents(1, &event);
	compute_time = sum(compute_time, toc(&start_time));
  
	start_time = tic();
        while (!output_queue.push(cur_idx)) ;
        //while (!output_queue.push(cur_idx)) nanosleep(&micro_sec, &micro_sec);
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

    int piece_idx = 0;
    int cur_idx;
    char* buffer = NULL;

    int num_scatter = 0;
    while (true) {
	timespec start_time = tic();
	if (piece_idx == 0) {
	    while (output_queue.read_available() <= 0) ;
	    //while (output_queue.read_available() <= 0) nanosleep(&micro_sec, &micro_sec);
	}
	//std::cout << "scatter: piece_idx = " << piece_idx << ", buffer_idx = " << cur_idx << std::endl;
	output_empty_time = sum(output_empty_time, toc(&start_time));

	start_time = tic();
	if (piece_idx == 0) {
            output_queue.pop(cur_idx);
	    //std::cout << "mapping: piece_idx = " << piece_idx << ", buffer_idx = " << cur_idx << std::endl;
            buffer = (char *)clEnqueueMapBuffer(commands, results[cur_idx], CL_TRUE, CL_MAP_READ, 0, TILE_SIZE*combine_rate, 0, NULL, NULL, NULL);
	}
	sOpenCL_time = sum(sOpenCL_time, toc(&start_time));

	start_time = tic();
        int sock;
        if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
            std::cerr << "Socket failed" << std::endl;
            exit(EXIT_FAILURE);
        }
    
        while (connect(sock, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) ;
	connect_time = sum(connect_time, toc(&start_time));

	start_time = tic();
	char* p = buffer + piece_idx*TILE_SIZE;
	int total_size = TILE_SIZE;
	int n;
        while ((n = write(sock, p, total_size)) > 0) {
	    if (n >= total_size) break;
	    p += n;
	    total_size -= n;
	}
	//write(sock, buffer+piece_idx*TILE_SIZE, TILE_SIZE);
	close(sock);
	scatter_time = sum(scatter_time, toc(&start_time));

	start_time = tic();
	if (piece_idx == combine_rate - 1) {
	    //std::cout << "releasing: piece_idx = " << piece_idx << ", buffer_idx = " << cur_idx << std::endl;
	    cl_event event;
            clEnqueueUnmapMemObject(commands, results[cur_idx], buffer, 0, NULL, &event);
            clWaitForEvents(1, &event);
	    //std::cout << "released!" << std::endl;
	}
	piece_idx = (piece_idx + 1) % combine_rate;
	sOpenCL_time = sum(sOpenCL_time, toc(&start_time));

	num_scatter++;
	//if (num_scatter % 10 == 0)
	    //std::cout << "Scatter " << num_scatter << " requests" << std::endl;
    }
}

int main(int argc, char* argv[]) {

    if (argc != 6) {
	std::cerr << "Wrong command-line format" << std::endl;
	exit(1);
    }

    signal(SIGINT, signal_callback_handler);
    signal(SIGTERM, signal_callback_handler);

    TILE_SIZE = atoi(argv[2]);

    throughput = atoi(argv[3]);

    combine_rate = atoi(argv[4]);

    meaningful_kernel = atoi(argv[5]);

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
    kernel = clCreateKernel(program, "aes", &err);
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
      contents[i] = clCreateBuffer(context, CL_MEM_WRITE_ONLY, TILE_SIZE*combine_rate, NULL, NULL);
      if (!contents[i])
      {
        printf("Error: Failed to allocate device memory!\n");
        printf("Test failed\n");
        return EXIT_FAILURE;
      }    
      results[i] = clCreateBuffer(context, CL_MEM_READ_ONLY, TILE_SIZE*combine_rate, NULL, NULL);
      if (!results[i])
      {
        printf("Error: Failed to allocate device memory!\n");
        printf("Test failed\n");
        return EXIT_FAILURE;
      }    
    }

    buf_ptr = 0;

    gOpenCL_time.tv_nsec = 0;
    gOpenCL_time.tv_sec = 0;
    sOpenCL_time.tv_nsec = 0;
    sOpenCL_time.tv_sec = 0;
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
    micro_sec.tv_sec = 0;
    micro_sec.tv_nsec = 1000;

    std::cout << "Start service:" << std::endl;
    boost::thread gather_thread(gather);
    boost::thread compute_thread(compute);
    boost::thread scatter_thread(scatter);

    gather_thread.join();
    compute_thread.join();
    scatter_thread.join();

    printTimeSpec(compute_time, "compute_time");
    printTimeSpec(gather_time, "gather_time");
    printTimeSpec(gOpenCL_time, "gOpenCL_time");
    printTimeSpec(scatter_time, "scatter_time");
    printTimeSpec(sOpenCL_time, "sOpenCL_time");

    printTimeSpec(accept_time, "accept_time");
    printTimeSpec(connect_time, "connect_time");

    printTimeSpec(input_full_time, "input_full_time");
    printTimeSpec(input_empty_time, "input_empty_time");
    printTimeSpec(output_full_time, "output_full_time");
    printTimeSpec(output_empty_time, "output_empty_time");

    for (i=0; i<NUM_BUFFERS; i++) {
	clReleaseMemObject(contents[i]);
	clReleaseMemObject(results[i]);
    }

    clReleaseProgram(program);
    clReleaseKernel(kernel);
    clReleaseCommandQueue(commands);
    clReleaseContext(context);

    return 0;
}
