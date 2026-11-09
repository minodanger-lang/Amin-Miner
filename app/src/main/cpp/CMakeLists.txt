cmake_minimum_required(VERSION 3.22.1)
project(aminminer LANGUAGES CXX)

add_library(aminminer SHARED native-lib.cpp)

target_compile_features(aminminer PRIVATE cxx_std_20)
target_compile_options(aminminer PRIVATE -Wall -Wextra -Wpedantic)
