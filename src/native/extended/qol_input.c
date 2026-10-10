/* Extended's Unicode EDIT bridge for the legacy Blitz message loop.
 * Only the current thread and this one control are intercepted. */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <imm.h>

static HWND search_edit, search_parent;
static HHOOK message_hook;
static BOOL added_clip_children;

static BOOL composing(void) {
    HIMC context = ImmGetContext(search_edit);
    BOOL active = context && ImmGetCompositionStringW(context, GCS_COMPSTR, NULL, 0) > 0;
    if (context) ImmReleaseContext(search_edit, context);
    return active;
}

static LRESULT CALLBACK search_message(int code, WPARAM removed, LPARAM data) {
    if (code >= 0 && removed == PM_REMOVE) {
        MSG *queued = (MSG *)data;
        if (queued->hwnd == search_edit) {
            MSG message = *queued;
            /* Blitz dispatches without TranslateMessage and uses DispatchMessageA.
             * Consume this control's message exactly once through Unicode APIs. */
            queued->hwnd = NULL;
            queued->message = WM_NULL;
            queued->wParam = 0;
            queued->lParam = 0;
            if (message.message == WM_KEYDOWN &&
                (message.wParam == VK_RETURN || message.wParam == VK_ESCAPE ||
                 message.wParam == VK_TAB) && !composing()) {
                SetFocus(search_parent);
            } else if (message.message == WM_KEYDOWN && message.wParam == 'A' &&
                       (GetKeyState(VK_CONTROL) & 0x8000)) {
                SendMessageW(search_edit, EM_SETSEL, 0, -1);
            } else {
                TranslateMessage(&message);
                DispatchMessageW(&message);
            }
        }
    }
    return CallNextHookEx(message_hook, code, removed, data);
}

void __stdcall QolInputDetach(void) {
    if (message_hook) UnhookWindowsHookEx(message_hook);
    message_hook = NULL;
    if (added_clip_children && IsWindow(search_parent)) {
        SetWindowLongW(search_parent, GWL_STYLE,
                       GetWindowLongW(search_parent, GWL_STYLE) & ~WS_CLIPCHILDREN);
        SetWindowPos(search_parent, NULL, 0, 0, 0, 0,
                     SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
    }
    search_edit = search_parent = NULL;
    added_clip_children = FALSE;
}

int __stdcall QolInputAttach(HWND edit, HWND parent) {
    DWORD thread = GetCurrentThreadId();
    LONG style;
    QolInputDetach();
    if (!IsWindowUnicode(edit) || GetParent(edit) != parent ||
        GetWindowThreadProcessId(edit, NULL) != thread) return 0;
    search_edit = edit;
    search_parent = parent;
    message_hook = SetWindowsHookExW(WH_GETMESSAGE, search_message, NULL, thread);
    if (!message_hook) {
        QolInputDetach();
        return 0;
    }
    style = GetWindowLongW(parent, GWL_STYLE);
    added_clip_children = !(style & WS_CLIPCHILDREN);
    SetWindowLongW(parent, GWL_STYLE, style | WS_CLIPCHILDREN);
    SetWindowPos(parent, NULL, 0, 0, 0, 0,
                 SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
    return 1;
}
