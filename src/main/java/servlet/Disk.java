package servlet;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.*;

public class Disk {
    final static int MAXDISK = 512*1024;        //总大小
    final static int MAXDISKBLOCK = 4*1024;     //每一个块的大小
    LinkedList<DiskBlockNode> diskBlockList;    //空闲盘块链
    DirectoryItem sroot;                        //根目录
    List<User>usertable;                   //用户表
    Map<Long,UserOpenFile> uoftable;           //用户打开文件表
    Map<Long,SystemOpenFile> softable;         //系统打开文件表
    Map<Long,FCB> fcbList;
    Disk(){
        /*
        初始化磁盘分区
         */

        //初始化根目录,用户进程打开文件表，系统打开文件表
        sroot = new DirectoryItem("root",Permission.RW_EXEC,Tag.DIRECTORY_TYPE);
        sroot.size = 0;
        sroot.lastModifyTime = new Date().getTime();
        sroot.parent = null;
        sroot.dirs = new ArrayList<>();

        usertable = new ArrayList<>();
        uoftable = new HashMap<>();
        softable = new HashMap<>();
        fcbList = new HashMap<>();
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
    @JSONField(serialize = false)
    public DirectoryItem parent;
    @JSONField(serialize = false)
    public int tag ;            //标志位，区分该表项指向指向一个目录，还是一个文件
    @JSONField(name = "label")
    public String name;         //文件名
    @JSONField(serialize = false)
    public long fcbid;
    @JSONField(serialize = false)
    public int permission;
    @JSONField(serialize = false)
    public long creatTime;
    @JSONField(serialize = false)
    public long lastModifyTime;
    @JSONField(serialize = false)
    public int size;
    @JSONField(name = "children")
    public List<DirectoryItem> dirs;
    DirectoryItem(){
        dirs = null;
    }
    DirectoryItem(String name,int permission,int tag){
        this.tag = tag;
        this.permission = permission;
        this.name = name;
        if (tag==Tag.DIRECTORY_TYPE)
            dirs = new ArrayList<>();
    }

    public int getTag() {
        return tag;
    }

    public void setTag(int tag) {
        this.tag = tag;
    }

    public long getFcbId() {
        return fcbid;
    }

    public void setFcbId(FCB fcb) {
        this.fcbid = fcbid;
    }

    public int getPermission() {
        return permission;
    }

    public void setPermission(int permission) {
        this.permission = permission;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<DirectoryItem> getDirs() {
        return dirs;
    }

    public void setDirs(List<DirectoryItem> dirs) {
        this.dirs = dirs;
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
    public int type;
    public int size;
    public int permission;
    public int usecount;
    public long creatTime;
    public long lastModifyTime;
    public LinkedList<DiskBlockNode> flist;
}
class UserOpenFile{
    public long uid;
    public String filename;
    public int mode;            //表示以什么样的模式打开文件（r:0,w:1,rw:2,aw:3）
    public int rpoint;
    public int wpoint;
    public long sid;
}
class SystemOpenFile{
    public long sid;
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