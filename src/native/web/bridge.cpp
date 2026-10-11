// Browser OS services called by the shared Blitz game through SystemProperty.
#include <emscripten.h>
#include <cstdlib>
#include <string>

EM_ASYNC_JS(char*, stranded_web_service, (const char* request), {
    const value = await Module.s2Bridge(UTF8ToString(request));
    return stringToNewUTF8(String(value));
});

std::string strandedWebProperty(const std::string& request) {
    char* result = stranded_web_service(request.c_str());
    std::string value = result ? result : "";
    std::free(result);
    return value;
}
