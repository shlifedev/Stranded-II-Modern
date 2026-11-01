#include <bb/stream/stream.h>
#include <algorithm>
#include <climits>
#include <cstdio>
#include <cstring>
#include <string>

BBMODULE_DECL(blitz);

// Substitute only byte delivery. The production ReadString and BBStr allocator
// run without a window, a graphics device or any player files.
struct MemoryStream : BBStream{
    std::string bytes;
    size_t position=0;
    int payloadLimit=INT_MAX,largestPayloadRequest=0;
    bool payloadError=false;

    MemoryStream(int length,const std::string &payload){
        bytes.assign(reinterpret_cast<const char*>(&length),4);
        bytes+=payload;
    }
    int read(char *destination,int requested) override{
        if(position>=4){
            largestPayloadRequest=std::max(largestPayloadRequest,requested);
            if(payloadError)return -1;
            requested=std::min(requested,payloadLimit);
        }
        int count=static_cast<int>(std::min<size_t>(requested,bytes.size()-position));
        std::memcpy(destination,bytes.data()+position,count);
        position+=count;
        return count;
    }
    int write(const char *,int) override{return 0;}
    int avail() override{return static_cast<int>(bytes.size()-position);}
    int eof() override{return position==bytes.size();}
};

static int failures=0;
static void expect(bool value,const char *message){
    std::printf("%s: %s\n",value?"PASS":"FAIL",message);
    if(!value)++failures;
}
static std::string readString(BBStream *stream){
    BBStr *value=bbReadString(stream);
    std::string result=*value;
    delete value;
    return result;
}

int main(){
    if(!blitz_create())return 2;
    for(bool debug:{false,true}){
        bb_env.debug=debug;
        std::printf("Stream checks: debug=%d\n",debug);
        expect(readString(nullptr).empty(),"null stream returns empty string");
        for(int headerBytes=0;headerBytes<4;++headerBytes){
            MemoryStream stream(123,"");stream.bytes.resize(headerBytes);
            expect(readString(&stream).empty(),"short length header returns empty string");
        }
        for(int length:{0,-1,INT_MIN}){
            MemoryStream stream(length,"untouched");
            expect(readString(&stream).empty()&&stream.position==4,
                   "zero and negative lengths leave the following payload untouched");
        }
        const std::string binary("A\0B\xff\n",5);
        MemoryStream small(static_cast<int>(binary.size()),binary+"NEXT");
        expect(readString(&small)==binary&&small.bytes.substr(small.position)=="NEXT",
               "embedded NUL/non-ASCII bytes preserve content and stream position");
        std::string large(4096*3+17,'x');
        for(size_t i=0;i<large.size();++i)large[i]=static_cast<char>(i);
        MemoryStream complete(static_cast<int>(large.size()),large+"NEXT");
        expect(readString(&complete)==large&&complete.bytes.substr(complete.position)=="NEXT",
               "multi-chunk strings preserve all bytes without overreading the next field");
        MemoryStream truncated(16,"ABC");
        expect(readString(&truncated)=="ABC","truncated payload returns only received bytes");
        MemoryStream shortRead(12,"abcdefghijkl");shortRead.payloadLimit=3;
        expect(readString(&shortRead)=="abc"&&shortRead.position==7,
               "short stream read returns its prefix without retrying or exposing a tail");
        MemoryStream empty(INT_MAX,"");
        expect(readString(&empty).empty()&&empty.largestPayloadRequest==4096,
               "INT_MAX length with no payload requires only a bounded read");
        MemoryStream damaged(INT_MAX,"x");
        expect(readString(&damaged)=="x"&&damaged.largestPayloadRequest==4096,
               "INT_MAX length with a short payload never creates a declared-size string");
        MemoryStream failed(12,"abc");failed.payloadError=true;
        expect(readString(&failed).empty(),"read error returns empty string");
        MemoryStream headerOnly(12,"");
        expect(readString(&headerOnly).empty(),"missing payload returns empty string");
    }
    blitz_destroy();
    return failures?1:0;
}
