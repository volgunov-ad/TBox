cmake_minimum_required(VERSION 3.22.1)
project(camdbench_sampler)

add_executable(camdbench_sampler camdbench_sampler.cpp)

add_custom_command(TARGET camdbench_sampler POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/../assets
    COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:camdbench_sampler> ${CMAKE_SOURCE_DIR}/../assets/camdbench_sampler
)
