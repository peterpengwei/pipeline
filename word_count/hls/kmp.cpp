#include "kmp.h"
#include <string.h>

#include "ap_int.h"

typedef ap_int<32> pattern_t;
typedef ap_int<512> uint512_t;

extern "C" {

#define WIDTH_FACTOR_CHAR 64

int kmp(pattern_t pattern_buf, uint512_t data_buf) {
#pragma HLS inline
  int matches = 0;
  for (int i=0; i<WIDTH_FACTOR_CHAR-PATTERN_SIZE+1; i++) {
#pragma HLS unroll
    int j;
    for (j=0; j<PATTERN_SIZE; j++) {
#pragma HLS unroll
      if (data_buf((i+j)*8+7,(i+j)*8) != pattern_buf(j*8+7, j*8))
	break;
    }
    if (j >= PATTERN_SIZE) matches++;
  }
  return matches;
}

void buffer_load(bool flag, uint512_t local_buf[CACHE_SIZE/WIDTH_FACTOR_CHAR], uint512_t* global_buf) {
#pragma HLS inline off
  int j;
  if (flag) {
      memcpy((void*)local_buf, (const void*)global_buf, sizeof(char)*(CACHE_SIZE));
  }
}

void buffer_compute(bool flag, uint512_t local_buf[CACHE_SIZE/WIDTH_FACTOR_CHAR], pattern_t pattern_buf, int& result) {
#pragma HLS inline off
  int j;
  if (flag) {
    for (j=0; j<CACHE_SIZE/WIDTH_FACTOR_CHAR; j++) {
#pragma HLS pipeline
      result += kmp(pattern_buf, local_buf[j]);
    }
  }

}

void workload(pattern_t* pattern, uint512_t* input, int string_size, int n_matches[1]) {
#pragma HLS INTERFACE m_axi port=pattern offset=slave bundle=gmem1
#pragma HLS INTERFACE m_axi port=input offset=slave bundle=gmem2
#pragma HLS INTERFACE m_axi port=n_matches offset=slave bundle=gmem3
#pragma HLS INTERFACE s_axilite port=pattern bundle=control
#pragma HLS INTERFACE s_axilite port=input bundle=control
#pragma HLS INTERFACE s_axilite port=string_size bundle=control
#pragma HLS INTERFACE s_axilite port=n_matches bundle=control
#pragma HLS INTERFACE s_axilite port=return bundle=control

    pattern_t pattern_buf = *pattern;
    uint512_t input_buf_x[CACHE_SIZE/WIDTH_FACTOR_CHAR];
    uint512_t input_buf_y[CACHE_SIZE/WIDTH_FACTOR_CHAR];

    int result = 0;

    for(int i=0; i<string_size/CACHE_SIZE+1; i++){
	if (i % 2 == 0) {
	    buffer_load(i < string_size/CACHE_SIZE, input_buf_x, input + i*CACHE_SIZE/WIDTH_FACTOR_CHAR);
	    buffer_compute(i > 0, input_buf_y, pattern_buf, result);
	}
	else {
	    buffer_load(i < string_size/CACHE_SIZE, input_buf_y, input + i*CACHE_SIZE/WIDTH_FACTOR_CHAR);
	    buffer_compute(i > 0, input_buf_x, pattern_buf, result);
	}
    }

    n_matches[0] = result;

    return;
}

}
