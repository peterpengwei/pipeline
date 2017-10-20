#include <boost/thread/thread.hpp>
#include <boost/lockfree/spsc_queue.hpp>
#include <iostream>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <cctype>
#include <string>

#define PORT 6070
#define TILE (1 << 20)
#define COUNT_DOWN (1 << 10)

boost::lockfree::spsc_queue<char*, boost::lockfree::capacity<8> > input_queue;
boost::lockfree::spsc_queue<char*, boost::lockfree::capacity<8> > output_queue;

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

    if (listen(server_fd, 8) < 0) {
        std::cerr << "Listen failed" << std::endl;
        exit(EXIT_FAILURE);
    }

    int count_down = COUNT_DOWN;
    int addrlen = sizeof(address);
    while (count_down--) {
        int instance = accept(server_fd, (struct sockaddr *) &address, (socklen_t *) &addrlen);
        if (instance < 0) {
            std::cerr << "Accept failed with countdown " << count_down << std::endl;
        }
        else {
            char* buffer = new char[TILE];
            read(instance, buffer, TILE);
	    //close(instance);
	    for (int i=0; i<64; i++) std::cout << buffer[i];
	    std::cout << std::endl;
            while (!input_queue.push(buffer)) ;
        }
    }
}

void compute(void) {
    char* buffer = NULL;
    
    int count_down = COUNT_DOWN;
    while (count_down--) {
        char* buffer = NULL;
        while (!input_queue.pop(buffer)) ;
        for (int i=0; i<TILE; i++) buffer[i] = toupper(buffer[i]);
        while (!output_queue.push(buffer)) ;
    }
}

void scatter(void) {
    int count_down = (1 << 5);
    
    sockaddr_in serv_addr;
    bzero(&serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(9520);
    if (inet_pton(AF_INET, "127.0.0.1", &(serv_addr.sin_addr)) <= 0) {
        std::cerr << "Inet_pton failed" << std::endl;
        exit(EXIT_FAILURE);
    }

    while (count_down--) {
        char* buffer = NULL;
        while (!output_queue.pop(buffer)) ;
	for (int i=0; i<64; i++) std::cout << buffer[i];
	std::cout << std::endl;

        int sock;
        if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
            std::cerr << "Socket failed" << std::endl;
            exit(EXIT_FAILURE);
        }
        if (connect(sock, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0) {
            std::cerr << "Connect failed with countdown: " << count_down << std::endl;
        }
        else {
            write(sock, buffer, TILE);
            delete [] buffer;
	    //close(sock);
        }
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
