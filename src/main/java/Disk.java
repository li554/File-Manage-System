import java.util.ArrayList;
import java.util.LinkedList;

public class Disk {
    final static int MAXDISK = 512*1024;        //总大小
    final static int MAXDISKBLOCK = 4*1024;     //每一个块的大小
    LinkedList<DiskBlockNode> diskBlockList;    //空闲盘块链
    DirectoryItem sroot;                        //根目录
    ArrayList<User>usertable;                   //用户表
    ArrayList<UserOpenFile> uoftable;           //用户打开文件表
    ArrayList<SystemOpenFile> softable;         //系统打开文件表
    Disk(){
        /*
        初始化磁盘分区
         */

        //初始化根目录,用户进程打开文件表，系统打开文件表
        sroot = new DirectoryItem();
        usertable = new ArrayList<>();
        uoftable = new ArrayList<>();
        softable = new ArrayList<>();

        //初始化空闲盘块链
        diskBlockList = new LinkedList<>();
        int n = Disk.MAXDISK/Disk.MAXDISKBLOCK;
        for (int i=0;i<n;i++){
            DiskBlockNode node = new DiskBlockNode();
            diskBlockList.add(node);
        }
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
    DiskBlockNode(){
        used = false;
        maxlength = Disk.MAXDISKBLOCK;       //设置磁盘块大小为4K
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
    public LinkedList<DiskBlockNode> flist;
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
    public DirectoryItem fitem;
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