# source - https://gist.github.com/sivachandran/3a0de157dccef822a230

include(CMakeParseArguments)

function(WRAP_STRING)
    set(oneValueArgs VARIABLE AT_COLUMN)
    cmake_parse_arguments(WRAP_STRING "${options}" "${oneValueArgs}" "" ${ARGN})

    string(LENGTH ${${WRAP_STRING_VARIABLE}} stringLength)
    math(EXPR offset "0")

    while(stringLength GREATER 0)

        if(stringLength GREATER ${WRAP_STRING_AT_COLUMN})
            math(EXPR length "${WRAP_STRING_AT_COLUMN}")
        else()
            math(EXPR length "${stringLength}")
        endif()

        string(SUBSTRING ${${WRAP_STRING_VARIABLE}} ${offset} ${length} line)
        set(lines "${lines}\n${line}")

        math(EXPR stringLength "${stringLength} - ${length}")
        math(EXPR offset "${offset} + ${length}")
    endwhile()

    set(${WRAP_STRING_VARIABLE} "${lines}" PARENT_SCOPE)
endfunction()

function(BIN2H)
    set(options APPEND NULL_TERMINATE)
    set(oneValueArgs SOURCE_FILE VARIABLE_NAME HEADER_FILE)
    cmake_parse_arguments(BIN2H "${options}" "${oneValueArgs}" "" ${ARGN})

    get_filename_component(SOURCE_FILE_PATH "${BIN2H_SOURCE_FILE}" ABSOLUTE BASE_DIR "${CMAKE_CURRENT_SOURCE_DIR}")
    get_filename_component(HEADER_FILE_PATH "${BIN2H_HEADER_FILE}" ABSOLUTE BASE_DIR "${CMAKE_CURRENT_BINARY_DIR}")
    set(BIN2H_SOURCE_FILE "${SOURCE_FILE_PATH}")
    set(BIN2H_HEADER_FILE "${HEADER_FILE_PATH}")

    if(EXISTS ${HEADER_FILE_PATH} AND ${HEADER_FILE_PATH} IS_NEWER_THAN ${SOURCE_FILE_PATH})
      return()
    endif()

    file(READ ${BIN2H_SOURCE_FILE} hexString HEX)
    string(LENGTH ${hexString} hexStringLength)

    if(BIN2H_NULL_TERMINATE)
        set(hexString "${hexString}00")
    endif()

    wrap_string(VARIABLE hexString AT_COLUMN 32)
    math(EXPR arraySize "${hexStringLength} / 2")

    string(REGEX REPLACE "([0-9a-f][0-9a-f])" "0x\\1, " arrayValues ${hexString})
    string(REGEX REPLACE ", $" "" arrayValues ${arrayValues})

    string(MAKE_C_IDENTIFIER "${BIN2H_VARIABLE_NAME}" BIN2H_VARIABLE_NAME)
    string(TOUPPER "${BIN2H_VARIABLE_NAME}" BIN2H_VARIABLE_NAME)

    set(arrayDefinition "const unsigned char ${BIN2H_VARIABLE_NAME}[] = { ${arrayValues} };")
    set(arraySizeDefinition "const size_t ${BIN2H_VARIABLE_NAME}_SIZE = ${arraySize};")

    set(declarations "${arrayDefinition}\n\n${arraySizeDefinition}\n\n")
    if(BIN2H_APPEND)
        file(APPEND ${BIN2H_HEADER_FILE} "${declarations}")
    else()
        file(WRITE ${BIN2H_HEADER_FILE} "${declarations}")
    endif()
endfunction()
