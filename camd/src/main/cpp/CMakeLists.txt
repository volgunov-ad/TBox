cmake_minimum_required(VERSION 3.22.1)
project(camd_grab)

add_executable(camd_grab camd_grab.cpp)
find_library(log-lib log)
target_link_libraries(camd_grab ${log-lib})

add_custom_command(TARGET camd_grab POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E make_directory ${CMAKE_SOURCE_DIR}/../assets
    COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:camd_grab> ${CMAKE_SOURCE_DIR}/../assets/camd_grab
)
