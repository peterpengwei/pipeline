#include "nw.h"
#include <string.h>
#include <inttypes.h>
#include "ap_int.h"

#define MATCH_SCORE 1
#define MISMATCH_SCORE -1
#define GAP_SCORE -1

#define ALIGN '\\'
#define SKIPA '^'
#define SKIPB '<'

#define MAX(A,B) ( ((A)>(B))?(A):(B) )

#define JOBS_PER_BATCH 512
#define UNROLL_FACTOR 64
#define JOBS_PER_PE ((JOBS_PER_BATCH)/(UNROLL_FACTOR))

typedef ap_uint<512> uint512_t;

extern "C"
{

void needwun(char SEQA[ALEN], char SEQB[BLEN],
             char alignedA[ALEN+BLEN], char alignedB[ALEN+BLEN]){

    char ptr[(ALEN+1)*(BLEN+1)];

    char M_former[ALEN+1];
    #pragma HLS ARRAY_PARTITION variable=M_former dim=0 complete
    char M_latter[ALEN+1];
    #pragma HLS ARRAY_PARTITION variable=M_latter dim=0 complete

    char score, up_left, up, left, max;
    int row, row_up, r;
    int a_idx, b_idx;
    int a_str_idx, b_str_idx;

    for (a_idx=0; a_idx<ALEN+1; a_idx++) {
    #pragma HLS UNROLL
        M_former[a_idx] = a_idx*GAP_SCORE;
    }
    for (a_idx=0; a_idx<ALEN+1; a_idx++) {
    #pragma HLS PIPELINE
        ptr[a_idx] = SKIPB;
    }

    // Matrix filling loop
    fill_out: for(b_idx=1; b_idx<(BLEN+1); b_idx++){
        fill_in: for(a_idx=0; a_idx<(ALEN+1); a_idx++){
	#pragma HLS PIPELINE
	#pragma HLS dependence variable=M_former inter false
	#pragma HLS dependence variable=M_latter inter false
	    if (a_idx == 0) {
	        M_latter[0] = b_idx * GAP_SCORE;
		ptr[b_idx*(ALEN+1)] = SKIPA;
	    }
	    else {
                if(SEQA[a_idx-1] == SEQB[b_idx-1]){
                    score = MATCH_SCORE;
                } else {
                    score = MISMATCH_SCORE;
                }

	        char x = M_former[ALEN];
                char y = M_former[0  ];
                char z = M_latter[ALEN];

                up_left = x + score;
                up      = y + GAP_SCORE;
                left    = z + GAP_SCORE;

                max = MAX(up_left, MAX(up, left));

                //M_latter[0] = max;

                row = (b_idx)*(ALEN+1);
                if(max == left){
                    ptr[row + a_idx] = SKIPB;
                } else if(max == up){
                    ptr[row + a_idx] = SKIPA;
                } else{
                    ptr[row + a_idx] = ALIGN;
                }
	    }
	    //-- shifting register
	    char tmp_former = M_former[0];
	    char tmp_latter = max;
	    for(int i=0; i<ALEN+1-1; i++){
	        M_former[i] = M_former[i+1] ; 
	        M_latter[i] = M_latter[i+1] ; 
	    }
	    M_former[ALEN+1-1] = tmp_former;
	    M_latter[ALEN+1-1] = tmp_latter;
        }

	for (int k=0; k<ALEN+1; k++) {
	#pragma HLS UNROLL
	    M_former[k] = M_latter[k];
	}
    }

    // TraceBack (n.b. aligned sequences are backwards to avoid string appending)
    a_idx = ALEN;
    b_idx = BLEN;
    a_str_idx = 0;
    b_str_idx = 0;

    trace: while(a_idx>0 || b_idx>0) {
    #pragma HLS PIPELINE
        r = b_idx*(ALEN+1);
        if (ptr[r + a_idx] == ALIGN){
            alignedA[a_str_idx++] = SEQA[a_idx-1];
            alignedB[b_str_idx++] = SEQB[b_idx-1];
            a_idx--;
            b_idx--;
        }
        else if (ptr[r + a_idx] == SKIPB){
            alignedA[a_str_idx++] = SEQA[a_idx-1];
            alignedB[b_str_idx++] = '-';
            a_idx--;
        }
        else{ // SKIPA
            alignedA[a_str_idx++] = '-';
            alignedB[b_str_idx++] = SEQB[b_idx-1];
            b_idx--;
        }
    }

    // Pad the result
    pad_a: for( ; a_str_idx<ALEN+BLEN; a_str_idx++ ) {
    #pragma HLS PIPELINE
      alignedA[a_str_idx] = '_';
    }
    pad_b: for( ; b_str_idx<ALEN+BLEN; b_str_idx++ ) {
    #pragma HLS PIPELINE
      alignedB[b_str_idx] = '_';
    }
}

void needwun_tiling(char* SEQA, char* SEQB,
             char* alignedA, char* alignedB) {
	for (int i=0; i<JOBS_PER_PE; i++) {
	    needwun(SEQA + i*ALEN, SEQB + i*BLEN,
		    alignedA + i*(ALEN+BLEN), alignedB + i*(ALEN+BLEN));
	}
	return;
}

void buffer_load(int flag, uint512_t* global_buf_A, uint512_t part_buf_A[ALEN*JOBS_PER_PE][UNROLL_FACTOR/64], uint512_t* global_buf_B, uint512_t part_buf_B[BLEN*JOBS_PER_PE][UNROLL_FACTOR/64]) {
#pragma HLS INLINE off
  if (flag) {
      memcpy(part_buf_A, global_buf_A, ALEN*JOBS_PER_PE*UNROLL_FACTOR);
      memcpy(part_buf_B, global_buf_B, BLEN*JOBS_PER_PE*UNROLL_FACTOR);
  }
  return;
}

void buffer_store(int flag, uint512_t* global_buf_A, uint512_t part_buf_A[(ALEN+BLEN)*JOBS_PER_PE][UNROLL_FACTOR/64], uint512_t* global_buf_B, uint512_t part_buf_B[(ALEN+BLEN)*JOBS_PER_PE][UNROLL_FACTOR/64]) {
#pragma HLS INLINE off
  if (flag) {
      memcpy(global_buf_A, part_buf_A, (ALEN+BLEN)*JOBS_PER_PE*UNROLL_FACTOR);
      memcpy(global_buf_B, part_buf_B, (ALEN+BLEN)*JOBS_PER_PE*UNROLL_FACTOR);
  }
  return;
}

void buffer_compute(int flag, uint512_t seqA_buf[ALEN*JOBS_PER_PE][UNROLL_FACTOR/64],
	                      uint512_t seqB_buf[BLEN*JOBS_PER_PE][UNROLL_FACTOR/64],
		              uint512_t alignedA_buf[(ALEN+BLEN)*JOBS_PER_PE][UNROLL_FACTOR/64],      
                              uint512_t alignedB_buf[(ALEN+BLEN)*JOBS_PER_PE][UNROLL_FACTOR/64]) {
#pragma HLS INLINE off

  char qryA[UNROLL_FACTOR][ALEN*JOBS_PER_PE];
#pragma HLS ARRAY_PARTITION variable=qryA dim=1 complete
  char qryB[UNROLL_FACTOR][BLEN*JOBS_PER_PE];
#pragma HLS ARRAY_PARTITION variable=qryB dim=1 complete
  char refA[UNROLL_FACTOR][(ALEN+BLEN)*JOBS_PER_PE];
#pragma HLS ARRAY_PARTITION variable=refA dim=1 complete
  char refB[UNROLL_FACTOR][(ALEN+BLEN)*JOBS_PER_PE];
#pragma HLS ARRAY_PARTITION variable=refB dim=1 complete

  int i, j;
  if (flag) {
    for (i=0; i<ALEN*JOBS_PER_PE; i++) {
#pragma HLS pipeline
      for (j=0; j<UNROLL_FACTOR; j++) {
#pragma HLS UNROLL
	qryA[j][i] = seqA_buf[i][j/64](j%64*8+7, j%64*8);
      }
    }

    for (i=0; i<BLEN*JOBS_PER_PE; i++) {
#pragma HLS pipeline
      for (j=0; j<UNROLL_FACTOR; j++) {
#pragma HLS UNROLL
	qryB[j][i] = seqB_buf[i][j/64](j%64*8+7, j%64*8);
      }
    }

    for (j=0; j<UNROLL_FACTOR; j++) {
#pragma HLS UNROLL
        needwun_tiling(qryA[j], qryB[j], refA[j], refB[j]);
    }

    for (i=0; i<(ALEN+BLEN)*JOBS_PER_PE; i++) {
#pragma HLS pipeline
      for (j=0; j<UNROLL_FACTOR; j++) {
#pragma HLS UNROLL
        alignedA_buf[i][j/64](j%64*8+7, j%64*8) = refA[j][i];
        alignedB_buf[i][j/64](j%64*8+7, j%64*8) = refB[j][i];
      }
    }
  }
  return;
}

void workload(uint512_t* SEQA, uint512_t* SEQB,
              uint512_t* alignedA, uint512_t* alignedB, int num_jobs) {
#pragma HLS INTERFACE m_axi port=SEQA offset=slave bundle=gmem
#pragma HLS INTERFACE m_axi port=SEQB offset=slave bundle=gmem
#pragma HLS INTERFACE m_axi port=alignedA offset=slave bundle=gmem
#pragma HLS INTERFACE m_axi port=alignedB offset=slave bundle=gmem
#pragma HLS INTERFACE s_axilite port=SEQA bundle=control
#pragma HLS INTERFACE s_axilite port=SEQB bundle=control
#pragma HLS INTERFACE s_axilite port=alignedA bundle=control
#pragma HLS INTERFACE s_axilite port=alignedB bundle=control
#pragma HLS INTERFACE s_axilite port=num_jobs bundle=control
#pragma HLS INTERFACE s_axilite port=return bundle=control

  int num_batches = num_jobs / JOBS_PER_BATCH;

  uint512_t seqA_buf_x[ALEN * JOBS_PER_PE][UNROLL_FACTOR/64];
  #pragma HLS ARRAY_PARTITION variable=seqA_buf_x complete dim=2
  uint512_t seqB_buf_x[BLEN * JOBS_PER_PE][UNROLL_FACTOR/64];
  #pragma HLS ARRAY_PARTITION variable=seqB_buf_x complete dim=2

  uint512_t seqA_buf_y[ALEN * JOBS_PER_PE][UNROLL_FACTOR/64];
  #pragma HLS ARRAY_PARTITION variable=seqA_buf_y complete dim=2
  uint512_t seqB_buf_y[BLEN * JOBS_PER_PE][UNROLL_FACTOR/64];
  #pragma HLS ARRAY_PARTITION variable=seqB_buf_y complete dim=2

  uint512_t alignedA_buf_x[(ALEN+BLEN) * JOBS_PER_PE][UNROLL_FACTOR/64];
  #pragma HLS ARRAY_PARTITION variable=alignedA_buf_x complete dim=2
  uint512_t alignedB_buf_x[(ALEN+BLEN) * JOBS_PER_PE][UNROLL_FACTOR/64];
  #pragma HLS ARRAY_PARTITION variable=alignedB_buf_x complete dim=2

  uint512_t alignedA_buf_y[(ALEN+BLEN) * JOBS_PER_PE][UNROLL_FACTOR/64];
  #pragma HLS ARRAY_PARTITION variable=alignedA_buf_y complete dim=2
  uint512_t alignedB_buf_y[(ALEN+BLEN) * JOBS_PER_PE][UNROLL_FACTOR/64];
  #pragma HLS ARRAY_PARTITION variable=alignedB_buf_y complete dim=2

  int i;
  for (i=0; i<num_batches+2; i++) {
    int load_flag = i >= 0 && i < num_batches;
    int compute_flag = i >= 1 && i < num_batches+1;
    int store_flag = i >= 2 && i < num_batches+2;
    if (i % 2 == 0) {
      buffer_compute(compute_flag, seqA_buf_y, seqB_buf_y, alignedA_buf_y, alignedB_buf_y);
      buffer_store(store_flag, alignedA+(i-2)*(ALEN+BLEN)*JOBS_PER_BATCH/64, alignedA_buf_x, alignedB+(i-2)*(ALEN+BLEN)*JOBS_PER_BATCH/64, alignedB_buf_x);
      buffer_load(load_flag, SEQA+i*ALEN*JOBS_PER_BATCH/64, seqA_buf_x, SEQB+i*BLEN*JOBS_PER_BATCH/64, seqB_buf_x);
    } 
    else {
      buffer_compute(compute_flag, seqA_buf_x, seqB_buf_x, alignedA_buf_x, alignedB_buf_x);
      buffer_store(store_flag, alignedA+(i-2)*(ALEN+BLEN)*JOBS_PER_BATCH/64, alignedA_buf_y, alignedB+(i-2)*(ALEN+BLEN)*JOBS_PER_BATCH/64, alignedB_buf_y);
      buffer_load(load_flag, SEQA+i*ALEN*JOBS_PER_BATCH/64, seqA_buf_y, SEQB+i*BLEN*JOBS_PER_BATCH/64, seqB_buf_y);
    } 
  }
  return;
}

}
