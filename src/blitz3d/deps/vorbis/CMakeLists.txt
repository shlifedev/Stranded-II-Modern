IF(TARGET ogg)
  set(CMAKE_UNITY_BUILD false)

  # use ogg in project tree
  file(READ tree/CMakeLists.txt FILE_CONTENTS)
  string(REGEX REPLACE "(^|\n)([ \t]*)find_package\\(Ogg" "\\1\\2# find_package(Ogg" FILE_CONTENTS "${FILE_CONTENTS}")
  file_write_changes(tree/CMakeLists.txt "${FILE_CONTENTS}")

  add_subdirectory(tree)
  target_output_to_toolchain(vorbis)
  target_output_to_toolchain(vorbisfile)
ENDIF()
