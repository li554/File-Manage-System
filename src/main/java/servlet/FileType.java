package servlet;

public class FileType {
    static final int SYSTEM_FILE = 1;
    static final int USER_FILE = 0;
}
class Mode{
    static final int READ_ONLY = 0;
    static final int WRITE_FIRST = 1;
    static final int WRITE_APPEND = 2;
    static final int READ_WRITE = 3;
}
class Permission{
    static final int READ_ONLY = 1;
    static final int WRITE_ONLY = 2;
    static final int EXECUTABLE = 4;
    static final int READ_WRITE = 3;
    static final int READ_EXEC = 5;
    static final int WRITE_EXEC = 6;
    static final int RW_EXEC = 7;
}
class Tag{
    static final int FILE_TYPE = 0;
    static final int DIRECTORY_TYPE = 1;
}
class Error{
    static final int DUPLICATION = -1;
    static final int PERMISSION_DENIED = -2;
    static final int PATH_NOT_FOUND = -3;
    static final int DISK_OVERFLOW = -4;
    static final int USING_BY_OTHERS = -5;
    static final int UNKNOWN = -6;
}
class Success{
    static final int WRITE = -7;
    static final int DELETE = -8;
    static final int CREATE = -9;
    static final int READ = -10;
    static final int RMDIR = -11;
    static final int MKDIR = -12;
    static final int PASTE = -13;
    static final int RENAME= -14;
    static final int CD = -15;
}