"""Generate native-pointer marshalling from the engine's typed command declarations."""
from pathlib import Path
import re
import sys

directory, module, output = sys.argv[1:]
pattern = re.compile(r'(\w+)([%#$]|\.\w+)?\((.*)\):"(\w+)"')
parameter = re.compile(r'(\w+)([%#$]|\.\w+)(?:=.*)?')
types = {'%': 'bb_int_t', '#': 'bb_float_t', '$': 'BBStr*', None: 'void'}
lines = [f'#include <bb/{module}/commands.h>', '#include <bb/blitz/legacy32.h>']
wrapped = []
for declaration in (Path(directory) / 'commands.decls').read_text().splitlines():
    match = pattern.fullmatch(declaration.strip())
    if not match:
        continue  # Internal compiler intrinsics have their own pointer-aware ABI.
    _, result, parameters, symbol = match.groups()
    args = []
    expressions = []
    pointers = []
    for index, item in enumerate(parameters.split(',') if parameters.strip() else []):
        found = parameter.fullmatch(item.strip())
        if not found:
            raise ValueError(f'Cannot marshal {declaration}: {item}')
        _, kind = found.groups()
        name = f'arg{index}'
        if kind.startswith('.'):
            args.append(f'bb_int_t {name}')
            expressions.append(f'static_cast<{kind[1:]}*>(s2Pointer({name}))')
            pointers.append(name)
        else:
            args.append(f'{types[kind]} {name}')
            expressions.append(name)
    pointer_result = result is not None and result.startswith('.')
    if not pointers and not pointer_result:
        continue
    wrapped.append(symbol)
    signature = 'bb_int_t' if pointer_result else types[result]
    invocation = f'{symbol}({", ".join(expressions)})'
    if pointer_result:
        statement = f'return s2Handle({invocation});'
    elif result is not None:
        statement = f'return {invocation};'
    else:
        statement = invocation + ';'
        # Freeing a resource invalidates its public handle permanently.
        if symbol.startswith(('bbFree', 'bbClose')) and len(pointers) == 1:
            statement += f' s2ForgetHandle({pointers[0]});'
    lines.append(f'extern "C" {signature} s2_{symbol}({", ".join(args)}) {{ {statement} }}')
generated = '\n'.join(lines) + '\n'
path = Path(output)
if not path.exists() or path.read_text() != generated:
    path.write_text(generated)
print(';'.join(wrapped), end='')
