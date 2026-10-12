#!/bin/bash

set -e

##
# Standard script for building LLVM on linux & macos.
#
LLVM_VERSION=19.1.5

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

BUILD_DIR=${1:-/build/llvm}
INSTALL_PREFIX=${2:-/opt/llvm}

rm -rf $BUILD_DIR
mkdir -p $BUILD_DIR

(
  cd $BUILD_DIR && \
  wget https://github.com/llvm/llvm-project/archive/refs/tags/llvmorg-$LLVM_VERSION.tar.gz && \
  tar xf llvmorg-$LLVM_VERSION.tar.gz
)

cmake -S $BUILD_DIR/llvm-project-llvmorg-$LLVM_VERSION/llvm -B $BUILD_DIR/build \
    -GNinja \
    -DCMAKE_TOOLCHAIN_FILE=$SCRIPT_DIR/llvm.cmake \
    -DCMAKE_INSTALL_PREFIX=$INSTALL_PREFIX

cmake --build $BUILD_DIR/build
cmake --install $BUILD_DIR/build

rm -rf $BUILD_DIR
