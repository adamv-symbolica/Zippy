#!/usr/bin/env bash
# Build the exact upstream dependencies used by the backend validation.
# Requires CMake >=3.22, a C++17 compiler, Git, and Boost headers.
set -euo pipefail
prefix=${1:?Usage: build_openfst.sh PREFIX [WORK_DIRECTORY]}
work=${2:-/tmp/zippy-openfst-dependencies}
jobs=${ZIPPY_BUILD_JOBS:-16}
mkdir -p "$work" "$prefix"
prefix=$(cd "$prefix" && pwd)
work=$(cd "$work" && pwd)
if [ ! -d "$work/abseil/.git" ]; then
  git clone --depth 1 --branch 20260817.0 https://github.com/abseil/abseil-cpp.git "$work/abseil"
fi
git -C "$work/abseil" fetch --depth 1 origin refs/tags/20260817.0
git -C "$work/abseil" checkout --detach FETCH_HEAD
cmake -S "$work/abseil" -B "$work/abseil-build" -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON -DABSL_PROPAGATE_CXX_STD=ON -DABSL_ENABLE_INSTALL=ON \
  -DCMAKE_CXX_STANDARD=17 -DCMAKE_INSTALL_PREFIX="$prefix"
cmake --build "$work/abseil-build" -j "$jobs"
cmake --install "$work/abseil-build"
if [ ! -d "$work/openfst/.git" ]; then
  git init "$work/openfst"
  git -C "$work/openfst" remote add origin https://github.com/google-research/openfst.git
fi
git -C "$work/openfst" fetch --depth 1 origin 5c887c63a882d608ec3010dba60ab25d57684b02
git -C "$work/openfst" checkout --detach FETCH_HEAD
cmake -S "$work/openfst" -B "$work/openfst-build" -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON -DOPENFST_BUILD_TESTS=OFF -DOPENFST_ENABLE_BIN=OFF \
  -DOPENFST_ENABLE_INSTALL=ON -DOPENFST_USE_SYSTEM_ABSEIL=ON \
  -Dabsl_DIR="$prefix/lib/cmake/absl" -DCMAKE_INSTALL_PREFIX="$prefix" \
  -DCMAKE_INSTALL_RPATH="$prefix/lib"
cmake --build "$work/openfst-build" -j "$jobs"
cmake --install "$work/openfst-build"
printf 'Installed OpenFst and Abseil in %s\n' "$prefix"
