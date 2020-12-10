package servlet;

import java.io.File;
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
        sroot.dirs = new ArrayList<>();
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
    public int permission;
    public ArrayList<DirectoryItem> dirs;
    DirectoryItem(){
        fcb = null;
        dirs = null;
    }
    DirectoryItem(String name,int permission,int tag){
        this.tag = tag;
        this.permission = permission;
        this.name = name;
        if (tag==Tag.DIRECTORY_TYPE)
            dirs = new ArrayList<>();
    }
}
class DiskBlockNode{
    public int maxlength;
    public byte[] content;
    DiskBlockNode(){
        content = null;
        maxlength = Disk.MAXDISKBLOCK;       //设置磁盘块大小为4K
    }
}
class FCB{
    public String filename;
    public int type;
    public int size;
    public int permission;
    public int usecount;
    public long creatTime;
    public long lastModifyTime;
    public LinkedList<DiskBlockNode> flist;
}
class UserOpenFile{
    public Integer uid;         //需要它作为一个引用类型，方便open函数和close函数使用
    public String filename;
    public int mode;            //表示以什么样的模式打开文件（r:0,w:1,rw:2,aw:3）
    public int rpoint;
    public int wpoint;
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
    User(String name,String psw,int permission){
        this.name = name;
        this.psw = psw;
        this.permission = permission;
        this.uroot = new DirectoryItem(name,permission,Tag.DIRECTORY_TYPE);
        this.uroot.dirs = new ArrayList<>();
    }
}