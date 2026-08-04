# Builds a 64-bit ocgcore shared library from a checkout of
# edo9300/ygopro-core (EDOPro-core), mirroring the settings of the
# repo's premake5.lua: C++17, RTTI off, static runtime, bundled Lua
# compiled as C++.
#
# Usage (from this directory):
#   cmake -B build -A x64 -DCORE_DIR=path/to/ygopro-core
#   cmake --build build --config Release
# Result: build/Release/ocgcore.dll -> copy into ../native/
cmake_minimum_required(VERSION 3.16)
project(ocgcore CXX)

set(CORE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../ygopro-core" CACHE PATH "Path to the ygopro-core checkout")
get_filename_component(CORE_DIR "${CORE_DIR}" ABSOLUTE)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# premake: staticruntime "on" - self-contained DLL, no vcredist dependency
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

# --- bundled Lua, compiled as C++ (premake: compileas "C++") ---
file(GLOB LUA_SOURCES "${CORE_DIR}/lua/src/*.c")
foreach(excluded lbitlib lcorolib ldblib linit loadlib loslib ltests lua luac lutf8lib onelua)
    list(REMOVE_ITEM LUA_SOURCES "${CORE_DIR}/lua/src/${excluded}.c")
endforeach()
set_source_files_properties(${LUA_SOURCES} PROPERTIES LANGUAGE CXX)

add_library(lua STATIC ${LUA_SOURCES})
target_include_directories(lua PUBLIC "${CORE_DIR}/lua/src" "${CORE_DIR}/lua")
if(MSVC)
    # premake: forceincludes { "luaconf-customize.h" }
    target_compile_options(lua PRIVATE "/FI${CORE_DIR}/lua/luaconf-customize.h")
else()
    target_compile_options(lua PRIVATE "-include" "${CORE_DIR}/lua/luaconf-customize.h")
endif()

# --- ocgcore shared library ---
file(GLOB CORE_SOURCES "${CORE_DIR}/*.cpp")
add_library(ocgcore SHARED ${CORE_SOURCES})
target_include_directories(ocgcore PRIVATE "${CORE_DIR}")
target_compile_definitions(ocgcore PRIVATE OCGCORE_EXPORT_FUNCTIONS NOMINMAX)
if(MSVC)
    target_compile_options(ocgcore PRIVATE /GR-) # premake: rtti "Off"
    target_compile_definitions(ocgcore PRIVATE WIN32 _WIN32)
endif()
target_link_libraries(ocgcore PRIVATE lua)
set_target_properties(ocgcore PROPERTIES PREFIX "") # ocgcore.dll, not libocgcore.dll
