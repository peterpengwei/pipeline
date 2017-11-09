#include <boost/thread/thread.hpp>
#include <boost/lockfree/spsc_queue.hpp>
#include <iostream>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <cctype>
#include <string>
#include <boost/atomic.hpp>
#include <signal.h>

#define PORT 6070
#define QUEUE_CAPACITY 32
#define NUM_BUFFERS ((QUEUE_CAPACITY)*(2))

int TILE_SIZE;

char* buffers[NUM_BUFFERS];
int buf_ptr;

boost::lockfree::spsc_queue<char*, boost::lockfree::capacity<QUEUE_CAPACITY> > input_queue;
boost::lockfree::spsc_queue<char*, boost::lockfree::capacity<QUEUE_CAPACITY> > output_queue;

void signal_callback_handler(int signum) {
    std::cout << "Caught signal " << signum << std::endl;

    for (int i=0; i<NUM_BUFFERS; i++) {
	delete [] buffers[i];
    }

    exit(signum);
}

void gather(void) {

    int server_fd;
    if ((server_fd = socket(AF_INET, SOCK_STREAM, 0)) == 0) {
        std::cerr << "Socket failed" << std::endl;
        exit(EXIT_FAILURE);
    }

    int opt = 1;
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt))) {
        std::cerr << "Setsockopt failed" << std::endl;
        exit(EXIT_FAILURE);
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

    if (listen(server_fd, 32) < 0) {
        std::cerr << "Listen failed" << std::endl;
        exit(EXIT_FAILURE);
    }

    int addrlen = sizeof(address);
    int num_gather = 0;
    while (true) {
	while (input_queue.write_available() <= 0) ;
        int instance = accept(server_fd, (struct sockaddr *) &address, (socklen_t *) &addrlen);
        if (instance < 0) {
            std::cerr << "Accept failed" << std::endl;
        }
        else {
            char* buffer = buffers[buf_ptr];
	    buf_ptr = (buf_ptr + 1) % NUM_BUFFERS;
	    int total_size = TILE_SIZE;
	    int n;
	    char* p = buffer;
            while ((n = read(instance, p, total_size)) > 0) {
	        if (n == total_size) break;
		p += n;
		total_size -= n;
	    }
	    close(instance);
            input_queue.push(buffer);
	    num_gather++;
	    //if (num_gather % 10 == 0)
	    //    std::cout << "Received " << num_gather << " requests" << std::endl;
        }
    }
}

void compute(void) {
    int num_compute = 0;
    while (true) {
        char* buffer = NULL;
        while (!input_queue.pop(buffer)) ;
        //for (int i=0; i<TILE_SIZE; i++) buffer[i] = toupper(buffer[i]);
        while (!output_queue.push(buffer)) ;
	num_compute++;
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

    int num_scatter = 0;
    while (true) {
        char* buffer = NULL;
        while (!output_queue.pop(buffer)) ;

        int sock;
        if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
            std::cerr << "Socket failed" << std::endl;
            exit(EXIT_FAILURE);
        }
    
        if (connect(sock, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) {
            std::cerr << "Connect failed" << std::endl;
        }
        write(sock, buffer, TILE_SIZE);
	close(sock);

	num_scatter++;
	//if (num_scatter % 10 == 0)
	//    std::cout << "Scatter " << num_scatter << " requests" << std::endl;
    }
}

int main(int argc, char* argv[]) {

    if (argc != 2) {
	std::cout << "Wrong command-line format" << std::endl;
	exit(1);
    }

    signal(SIGINT, signal_callback_handler);
    signal(SIGTERM, signal_callback_handler);

    TILE_SIZE = atoi(argv[1]);

    int i;
    for (i=0; i<NUM_BUFFERS; i++) {
	buffers[i] = new char[TILE_SIZE];
    }
    buf_ptr = 0;

    boost::thread gather_thread(gather);
    boost::thread compute_thread(compute);
    boost::thread scatter_thread(scatter);

    gather_thread.join();
    compute_thread.join();
    scatter_thread.join();

    for (i=0; i<NUM_BUFFERS; i++) {
	delete [] buffers[i];
    }

    return 0;
}
