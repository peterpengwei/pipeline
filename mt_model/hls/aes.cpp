#include "aes.h"
#include <string.h>
#include "ap_int.h"
#include <iostream>

typedef ap_uint<128> uint128_t;
typedef ap_uint<256> uint256_t;
typedef ap_uint<512> uint512_t;

extern "C" {

#define BUF_SIZE_OFFSET 20
#define BUF_SIZE ((1) << (BUF_SIZE_OFFSET))

void aes(uint512_t* input, uint512_t* output, int data_size, int flag) {
#pragma HLS INTERFACE m_axi port=input offset=slave bundle=gmem1
#pragma HLS INTERFACE m_axi port=output offset=slave bundle=gmem1
#pragma HLS INTERFACE s_axilite port=input bundle=control 
#pragma HLS INTERFACE s_axilite port=output bundle=control 
#pragma HLS INTERFACE s_axilite port=data_size bundle=control 
#pragma HLS INTERFACE s_axilite port=flag bundle=control 
#pragma HLS INTERFACE s_axilite port=return bundle=control 

  if (flag) {
      uint512_t buffer[BUF_SIZE/64];

      if (data_size <= BUF_SIZE) {
          memcpy(buffer, input, data_size);
          memcpy(output, buffer, data_size);
      }
      else {
          int num_batches = data_size / BUF_SIZE;
          for (int i=0; i<num_batches; i++) {
              memcpy(buffer, input + BUF_SIZE/64 * i, BUF_SIZE);
              memcpy(output + BUF_SIZE/64 * i, buffer, BUF_SIZE);
          }
	  int remainder = data_size % BUF_SIZE;
	  if (remainder != 0) {
	      memcpy(buffer, input + BUF_SIZE/64 * num_batches, remainder);
	      memcpy(output + BUF_SIZE/64 * num_batches, buffer, remainder);
	  }
      }
  }

  return;
}

}
