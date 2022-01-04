// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package tdmsg

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestTDMsgV4(t *testing.T) {
	b := []byte{15, 4, 0, 0, 2, 129, 39, 0, 0, 0, 0, 0, 4, 97, 138, 19, 151, 0, 1, 0, 6, 168, 171, 0, 0, 1, 254, 180, 5, 168, 0, 0, 2, 176, 106, 111, 115, 105, 101, 106, 121, 99, 104, 101, 110, 9, 108, 105, 103, 104, 116, 9, 110, 117, 108, 108, 9, 83, 69, 76, 69, 67, 84, 32, 42, 32, 102, 114, 111, 109, 32, 40, 32, 13, 16, 64, 112, 114, 111, 100, 117, 99, 116, 95, 105, 100, 44, 99, 111, 110, 116, 101, 110, 5, 11, 0, 32, 13, 1, 104, 99, 111, 117, 110, 116, 40, 68, 73, 83, 84, 73, 78, 67, 84, 32, 111, 114, 100, 101, 114, 95, 105, 100, 41, 32, 65, 83, 13, 13, 8, 110, 117, 109, 21, 46, 12, 115, 117, 109, 40, 9, 35, 128, 112, 114, 105, 99, 101, 41, 32, 70, 82, 79, 77, 32, 112, 99, 103, 95, 101, 99, 109, 95, 99, 111, 109, 109, 111, 110, 58, 58, 116, 95, 100, 119, 100, 5, 18, 8, 107, 100, 95, 9, 47, 184, 100, 101, 116, 97, 105, 108, 95, 102, 104, 32, 87, 72, 69, 82, 69, 32, 100, 115, 32, 61, 32, 99, 97, 115, 116, 40, 99, 111, 110, 99, 97, 116, 40, 50, 48, 50, 49, 48, 56, 50, 57, 44, 39, 50, 51, 39, 41, 1, 126, 144, 66, 73, 71, 73, 78, 84, 41, 32, 32, 32, 97, 110, 100, 32, 114, 101, 103, 101, 120, 112, 95, 114, 101, 112, 108, 97, 99, 101, 40, 115, 117, 98, 115, 116, 114, 40, 111, 5, 175, 128, 112, 97, 121, 95, 116, 105, 109, 101, 44, 49, 44, 49, 48, 41, 44, 39, 45, 39, 44, 39, 39, 41, 32, 98, 101, 116, 119, 101, 101, 110, 32, 50, 48, 1, 91, 4, 48, 49, 5, 72, 9, 13, 168, 50, 57, 32, 32, 32, 65, 78, 68, 32, 112, 114, 105, 95, 115, 111, 114, 116, 32, 61, 32, 39, 229, 140, 187, 232, 141, 175, 229, 129, 165, 229, 186, 183, 39, 32, 71, 82, 79, 85, 80, 32, 66, 89, 61, 64, 0, 32, 61, 65, 60, 32, 41, 32, 97, 32, 105, 110, 110, 101, 114, 32, 106, 111, 105, 110, 32, 53, 111, 16, 115, 97, 97, 115, 95, 61, 116, 45, 104, 24, 32, 116, 105, 116, 108, 101, 44, 37, 55, 0, 44, 33, 41, 20, 105, 115, 115, 105, 111, 110, 98, 65, 1, 4, 105, 109, 37, 65, 28, 98, 111, 117, 116, 105, 113, 117, 101, 21, 82, 4, 102, 100, 46, 66, 1, 21, 206, 32, 103, 114, 111, 117, 112, 32, 98, 121, 32, 94, 127, 0, 0, 32, 9, 129, 0, 32, 70, 130, 0, 40, 41, 32, 98, 32, 111, 110, 32, 97, 46, 112, 114, 81, 41, 16, 32, 61, 32, 98, 46, 58, 74, 0, 4, 32, 111, 33, 108, 1, 99, 5, 9, 76, 95, 110, 117, 109, 32, 68, 69, 83, 67, 9, 49, 54, 51, 48, 52, 56, 52, 49, 48, 53, 21, 11, 56, 52, 50, 9, 115, 117, 99, 99, 101, 115, 115, 9, 110, 117, 108, 108, 0, 106, 98, 105, 100, 61, 98, 95, 116, 101, 103, 95, 116, 100, 119, 95, 109, 101, 116, 97, 100, 97, 116, 97, 95, 106, 111, 117, 114, 110, 97, 108, 95, 108, 111, 103, 38, 116, 105, 100, 61, 109, 101, 116, 97, 100, 97, 116, 97, 95, 105, 100, 101, 120, 95, 108, 111, 103, 115, 95, 112, 99, 103, 38, 109, 115, 103, 85, 85, 73, 68, 61, 50, 48, 100, 48, 49, 99, 49, 53, 45, 102, 101, 57, 52, 45, 52, 54, 99, 101, 45, 57, 102, 101, 98, 45, 101, 55, 100, 51, 101, 57, 97, 52, 100, 57, 102, 49, 238, 1, 15, 4}
	tm, err := New(b)
	assert.Nil(t, err)
	assert.Equal(t, uint64(1636438935000), tm.CreateTime)
	assert.Equal(t, int32(4), tm.Version)
	assert.Equal(t, uint32(1), tm.MsgCount)
	assert.Equal(t, false, tm.IsNumBid)
}

func TestTDMsgV1(t *testing.T) {
	b := []byte{15, 1, 0, 0, 1, 125, 71, 98, 161, 138, 0, 0, 0, 1, 0, 206, 100, 116, 61, 49, 54, 51, 55, 53, 56, 48, 49, 56, 53, 57, 56, 55, 38, 109, 115, 103, 85, 85, 73, 68, 61, 100, 98, 52, 97, 51, 101, 51, 100, 45, 50, 100, 101, 55, 45, 52, 99, 102, 102, 45, 56, 54, 97, 101, 45, 98, 53, 55, 52, 55, 101, 57, 49, 98, 51, 101, 53, 38, 99, 110, 116, 61, 49, 38, 109, 116, 61, 112, 98, 38, 78, 111, 100, 101, 73, 80, 61, 49, 49, 46, 49, 53, 52, 46, 50, 48, 57, 46, 49, 55, 57, 38, 114, 116, 61, 49, 54, 51, 55, 53, 56, 48, 49, 56, 53, 57, 57, 52, 38, 109, 61, 57, 38, 116, 105, 100, 61, 116, 95, 115, 110, 103, 95, 103, 100, 116, 95, 117, 110, 105, 111, 110, 95, 100, 105, 115, 112, 108, 97, 121, 95, 98, 101, 102, 111, 114, 101, 95, 114, 101, 118, 105, 101, 119, 95, 114, 101, 116, 114, 121, 38, 98, 105, 100, 61, 98, 95, 115, 110, 103, 95, 103, 100, 116, 95, 117, 110, 105, 111, 110, 95, 100, 105, 115, 112, 108, 97, 121, 95, 98, 101, 102, 111, 114, 101, 95, 114, 101, 118, 105, 101, 119, 0, 0, 0, 107, 1, 103, 240, 102, 0, 0, 0, 99, 8, 229, 207, 250, 8, 16, 195, 174, 144, 202, 167, 145, 226, 10, 24, 180, 186, 189, 191, 16, 32, 237, 128, 128, 128, 80, 40, 236, 158, 235, 159, 6, 48, 164, 235, 176, 209, 235, 206, 4, 56, 146, 188, 189, 191, 16, 64, 12, 72, 225, 6, 80, 1, 88, 0, 154, 1, 9, 49, 48, 48, 55, 55, 48, 55, 57, 48, 160, 1, 10, 168, 1, 187, 80, 176, 1, 211, 228, 237, 140, 6, 184, 1, 134, 159, 157, 209, 218, 221, 173, 191, 16, 192, 1, 147, 254, 176, 191, 16, 15, 1}
	tm, err := New(b)
	assert.Equal(t, uint64(1637580185994), tm.CreateTime)
	assert.Equal(t, int32(1), tm.Version)
	assert.Equal(t, false, tm.IsNumBid)
	assert.Nil(t, err)
}
