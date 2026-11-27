if(NOT BB_DESKTOP AND NOT BB_NDK AND NOT BB_NX)
  return()
endif()

set(CMAKE_CXX_STANDARD 14)

add_library(jpeg OBJECT src/Source/LibJPEG/jaricom.c src/Source/LibJPEG/jcapimin.c src/Source/LibJPEG/jcapistd.c src/Source/LibJPEG/jcarith.c src/Source/LibJPEG/jccoefct.c src/Source/LibJPEG/jccolor.c src/Source/LibJPEG/jcdctmgr.c src/Source/LibJPEG/jchuff.c src/Source/LibJPEG/jcinit.c src/Source/LibJPEG/jcmainct.c src/Source/LibJPEG/jcmarker.c src/Source/LibJPEG/jcmaster.c src/Source/LibJPEG/jcomapi.c src/Source/LibJPEG/jcparam.c src/Source/LibJPEG/jcprepct.c src/Source/LibJPEG/jcsample.c src/Source/LibJPEG/jctrans.c src/Source/LibJPEG/jdapimin.c src/Source/LibJPEG/jdapistd.c src/Source/LibJPEG/jdarith.c src/Source/LibJPEG/jdatadst.c src/Source/LibJPEG/jdatasrc.c src/Source/LibJPEG/jdcoefct.c src/Source/LibJPEG/jdcolor.c src/Source/LibJPEG/jddctmgr.c src/Source/LibJPEG/jdhuff.c src/Source/LibJPEG/jdinput.c src/Source/LibJPEG/jdmainct.c src/Source/LibJPEG/jdmarker.c src/Source/LibJPEG/jdmaster.c src/Source/LibJPEG/jdmerge.c src/Source/LibJPEG/jdpostct.c src/Source/LibJPEG/jdsample.c src/Source/LibJPEG/jdtrans.c src/Source/LibJPEG/jerror.c src/Source/LibJPEG/jfdctflt.c src/Source/LibJPEG/jfdctfst.c src/Source/LibJPEG/jfdctint.c src/Source/LibJPEG/jidctflt.c src/Source/LibJPEG/jidctfst.c src/Source/LibJPEG/jidctint.c src/Source/LibJPEG/jmemmgr.c src/Source/LibJPEG/jmemnobs.c src/Source/LibJPEG/jquant1.c src/Source/LibJPEG/jquant2.c src/Source/LibJPEG/jutils.c src/Source/LibJPEG/transupp.c)
target_include_directories(jpeg PUBLIC src/Source/LibJPEG)
set_target_properties(jpeg PROPERTIES UNITY_BUILD false)


set(SOURCES
  src/Source/FreeImage/BitmapAccess.cpp
  src/Source/FreeImage/FreeImage.cpp
  src/Source/FreeImage/FreeImageIO.cpp
  src/Source/FreeImage/GetType.cpp
  src/Source/FreeImage/LFPQuantizer.cpp
  src/Source/FreeImage/MemoryIO.cpp
  src/Source/FreeImage/PixelAccess.cpp
  src/Source/FreeImage/Plugin.cpp
  src/Source/FreeImage/PluginBMP.cpp
  src/Source/FreeImage/PluginDDS.cpp
  src/Source/FreeImage/PluginIFF.cpp
  src/Source/FreeImage/PluginJPEG.cpp
  src/Source/FreeImage/PluginPCX.cpp
  src/Source/FreeImage/PluginPNG.cpp
  src/Source/FreeImage/PluginTARGA.cpp
  src/Source/FreeImage/Conversion.cpp
  src/Source/FreeImage/Conversion16_555.cpp
  src/Source/FreeImage/Conversion16_565.cpp
  src/Source/FreeImage/Conversion24.cpp
  src/Source/FreeImage/Conversion32.cpp
  src/Source/FreeImage/Conversion8.cpp
  src/Source/FreeImage/ConversionRGB16.cpp
  src/Source/FreeImage/ConversionRGBF.cpp
  src/Source/FreeImage/NNQuantizer.cpp
  src/Source/FreeImage/WuQuantizer.cpp
  src/Source/Metadata/Exif.cpp
  src/Source/Metadata/FreeImageTag.cpp
  src/Source/Metadata/IPTC.cpp
  src/Source/Metadata/TagLib.cpp
  src/Source/FreeImageToolkit/ClassicRotate.cpp
  src/Source/FreeImageToolkit/Flip.cpp

)

set(DEPS jpeg png_static)

add_library(freeimage STATIC ${SOURCES})
target_output_to_toolchain(freeimage)
target_compile_definitions(freeimage PRIVATE -DLIBRAW_NODLL)
target_compile_definitions(freeimage PUBLIC -DFREEIMAGE_LIB)
target_include_directories(freeimage PUBLIC src/Source)

target_link_libraries(freeimage ${DEPS} png_static)
set_target_properties(freeimage PROPERTIES UNITY_BUILD false)
