
#ifndef PREFS_H
#define PREFS_H

class Prefs{
public:
	bool prg_debug;
	std::string prg_lastbuild;

	RECT win_rect;
	bool win_maximized;
	bool win_notoolbar;

	std::string font_editor,font_tabs,font_debug;
	int font_editor_height,font_tabs_height,font_debug_height;

	int rgb_bkgrnd;
	int rgb_string;
	int rgb_ident;
	int rgb_keyword;
	int rgb_comment;
	int rgb_digit;
	int rgb_default;

	int edit_tabs;
	bool edit_blkcursor;
	int edit_backup;

	std::string img_toolbar;

	std::string homeDir;
	CFont conFont,editFont,tabsFont,debugFont;

	std::vector<std::string> recentFiles;

	std::string cmd_line;

	void open();
	void close();

private:

	void setDefault();
	void createFonts();
};

extern Prefs prefs;

#endif