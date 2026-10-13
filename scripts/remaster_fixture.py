"""Actual renderer settings logic with explicit graphics side-effect doubles."""
from verify_timing import function


def settings_fixture(includes):
    text = (includes / 'remaster.bb').read_text('utf8')
    body = text[:text.index('Function ')]
    for name in ('remaster_init', 'remaster_option', 'remaster_setting_owned',
                 'remaster_settings_reset', 'remaster_settings_write'):
        body += function(text, name) + '\n'
    # GPU resource refresh is covered by the render fixtures, not this console test.
    body += '''Function remaster_settings_apply()
 For i=0 To 3:remaster_enabled(i)=remaster_pending(i):Next
End Function
remaster_init()
'''
    return body
