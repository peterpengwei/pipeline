#include <boost/thread/thread.hpp>
#include <boost/lockfree/spsc_queue.hpp>
#include <iostream>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <cctype>
#include <string>
#include <boost/atomic.hpp>

#define PORT 6070
#define TILE (1 << 20)

boost::lockfree::spsc_queue<char*, boost::lockfree::capacity<32> > input_queue;
boost::lockfree::spsc_queue<char*, boost::lockfree::capacity<32> > output_queue;

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
        int instance = accept(server_fd, (struct sockaddr *) &address, (socklen_t *) &addrlen);
        if (instance < 0) {
            std::cerr << "Accept failed" << std::endl;
        }
        else {
            char* buffer = new char[TILE];
	    int n;
	    int total_size = TILE;
	    char* p = buffer;
	    while ((n = read(instance, p, total_size)) > 0) {
	        if (n == total_size) break;
		p = p + n;
		total_size -= n;
	    }
	    close(instance);
            while (!input_queue.push(buffer)) ;
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
        //for (int i=0; i<TILE; i++) buffer[i] = toupper(buffer[i]);
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
        write(sock, buffer, TILE);
        delete [] buffer;
	close(sock);

	num_scatter++;
	//if (num_scatter % 10 == 0)
	//    std::cout << "Scatter " << num_scatter << " requests" << std::endl;
    }
}

int main(int argc, char* argv[]) {

    boost::thread gather_thread(gather);
    boost::thread compute_thread(compute);
    boost::thread scatter_thread(scatter);

    gather_thread.join();
    compute_thread.join();
    scatter_thread.join();

    return 0;
}
