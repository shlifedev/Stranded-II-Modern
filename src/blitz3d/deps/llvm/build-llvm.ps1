$ErrorActionPreference = "Stop"

$LLVM_VERSION = "19.1.5"
$BUILD_DIR = ".\build"
$DEST_DIR = ".\llvm"
$ARCHIVE = ".\llvm-$LLVM_VERSION.zip"

if (-not(Test-Path -Path $ARCHIVE -PathType Leaf)) {
  Invoke-WebRequest -Uri "https://github.com/llvm/llvm-project/archive/refs/tags/llvmorg-$LLVM_VERSION.zip" -OutFile $ARCHIVE
}

if (-not(Test-Path -Path llvm-project-llvmorg-$LLVM_VERSION)) {
  # Expand-Archive seems slow so use 7zip if available
  if (Get-Command "7z.exe" -ErrorAction SilentlyContinue) {
    7z x $ARCHIVE
  } else {
    Expand-Archive $ARCHIVE -DestinationPath .\
  }
}

cmake -S llvm-project-llvmorg-$LLVM_VERSION\llvm -B $BUILD_DIR `
  -G Ninja `
  -DCMAKE_TOOLCHAIN_FILE:FILEPATH=..\..\llvm.cmake `
  -DCMAKE_INSTALL_PREFIX="$DEST_DIR"

cmake --build $BUILD_DIR
cmake --install $BUILD_DIR
