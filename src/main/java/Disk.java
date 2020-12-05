import java.util.ArrayList;

public class Disk {
    final static int MAXDISK = 512*1024;
    final static int MAXDISKBLOCK = 4*1024;
    DiskBlockList diskBlockList;
    DirectoryItem sroot;             //根目录
    ArrayList<User>usertable;
    ArrayList<UserOpenFile> uoftable;
    ArrayList<SystemOpenFile> softable;
    Disk(){
        //初始化分区,目录表,用户进程打开文件表，系统打开文件表
        sroot = new DirectoryItem();
        usertable = new ArrayList<>();
        diskBlockList = new DiskBlockList();
        uoftable = new ArrayList<>();
        softable = new ArrayList<>();
    }
}
class DirectoryItem{            //目录索引项,索引可能是文件的索引，也可能是目录的索引
    public int tag ;            //标志位，区分该表项指向指向一个目录，还是一个文件
    public String name;         //文件名
    public FCB fcb;
    public ArrayList<DirectoryItem> dirs;
    DirectoryItem(){
        tag = 1;                //默认为目录
        fcb = null;
        dirs = null;
    }
    public void dirinit(){
        if (tag==1)
            dirs = new ArrayList<>();
        else
            System.out.println("当前对象为文件，不能进行目录初始化");
    }
    public void fileinit(){
        if (tag==0)
            fcb = new FCB();
        else
            System.out.println("当前对象为目录，不能进行文件初始化");
    }
}
class DiskBlockNode{
    public int maxlength;
    public int start;
    public boolean used;
    public DiskBlockNode next;
    DiskBlockNode(){
        used = false;
        maxlength = Disk.MAXDISKBLOCK;       //设置磁盘块大小为4K
    }
}
class DiskBlockList{
    public DiskBlockNode head;
    public int left;
    DiskBlockList(){
        head = new DiskBlockNode();
        //512/4 = 128块
        int n = Disk.MAXDISK/Disk.MAXDISKBLOCK;
        left = n;
        //建立盘块链
        for (int i=n-1;i>=0;i--) {
            DiskBlockNode node = new DiskBlockNode();
            node.start = i * 4096;
            node.next = head.next;
            head.next = node;
        }
    }
}
class FCB{
    public String filename;
    public int type;
    public int start;
    public int size;
    public int permission;
    public int usecount;
    public long creatTime;
    public long lastModifyTime;
    public DiskBlockNode fhead;
}
class UserOpenFile{
    public int uid;
    public String filename;
    public int rwlocation;
    public int sid;
}

class SystemOpenFile{
    public int sid;
    public String filename;
    public String path;
    public int opencount;
}

class User{
    public String name;
    public String psw;
    public int permission;
    public DirectoryItem uroot;
    User(int limit){
        permission = limit;
        uroot = new DirectoryItem();
        uroot.name = this.name;
        uroot.dirs = new ArrayList<>();
    }
}