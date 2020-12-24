package servlet;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Disk {
    final static int DISK_SIZE = 512*1024;        //总大小
    LinkedList<DiskBlockNode> diskBlockList;    //空闲盘块链
    DirectoryItem sroot;                        //根目录
    Map<Long,Inode> inodeMap;
    Disk(){
        //初始化索引节点目录
        inodeMap = new HashMap<>();
        //初始化根目录,在索引节点的第一个即根目录文件
        sroot = new DirectoryItem("root",0);
        Inode inode = new Inode(0,FileType.FOLDER_FILE,0);
        inodeMap.put((long) 0,inode);

        //初始化空闲盘块链
        diskBlockList = new LinkedList<>();
        int n = Disk.DISK_SIZE/DiskBlockNode.NODE_SIZE;
        for (int i=0;i<n;i++){
            DiskBlockNode node = new DiskBlockNode();
            diskBlockList.add(node);
        }
    }
}
class DirectoryItem{            //目录索引项
    public String name;         //文件名
    public long inodeid;          //索引节点号
    DirectoryItem(String name,long inodeid){
        this.name = name;
        this.inodeid = inodeid;
    }
    public List<DirectoryItem> getDirs(Disk disk) throws UnsupportedEncodingException {
        Inode root = disk.inodeMap.get(inodeid);
        if (root.type==FileType.PLAIN_FILE) return null;
        StringBuilder content = new StringBuilder();
        for (DiskBlockNode node:root.flist){
            String str = new String(node.content);
            Pattern pattern = Pattern.compile("([^\u0000]*)");
            Matcher matcher = pattern.matcher(str);
            if(matcher.find(0)){
                content.append(new String(matcher.group(1).getBytes("utf-8")));
            }
        }
        List<DirectoryItem> list = new ArrayList<>();
        String[] a = content.toString().split(";");
        if (a[0].equals("")) return list;
        for (int i=0;i<a.length;i++){
            String[] b = a[i].split(",");
            DirectoryItem item = new DirectoryItem(b[0],Long.parseLong(b[1]));
            list.add(item);
        }
        return list;
    }
    public DirectoryItem getParent(Disk disk) throws UnsupportedEncodingException {
        List<DirectoryItem>dirs = this.getDirs(disk);
        if (dirs==null) return null;
        return dirs.get(0);
    }
}
class DiskBlockNode{
    final static int NODE_SIZE = 4*1024;     //每一个块的大小
    public byte[] content;
    DiskBlockNode(){
        content = null;
    }
}
class Inode{
    public long uid;
    public int type;
    public int size;
    public long creatTime;
    public long lastModifyTime;
    public LinkedList<DiskBlockNode> flist;
    public Map<Long,ACLItem> acl;
    Inode(long uid,int type,long time){
        this.uid = uid;
        this.type = type;
        this.creatTime = time;
        this.lastModifyTime = time;
        this.size = 0;
        acl = new HashMap<>();
        flist = new LinkedList<>();
    }
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
    public long uid;
    public String name;
    public String psw;
    public DirectoryItem uroot;
    public Map<Long,UserOpenFile> uoftable;           //用户打开文件表
    public Map<Long,CLItem>visitList;
    User(String name,String psw){
        this.uid = new Date().getTime();
        this.name = name;
        this.psw = psw;
        this.uoftable = new HashMap<>();
        this.visitList = new HashMap<>();
    }
}
class CLItem{       //访问权限表
    public long fcbid;
    public int R;
    public int W;
    public int E;
    CLItem(long fcbid,int R,int W,int E){
        this.fcbid = fcbid;
        this.R = R;
        this.W = W;
        this.E = E;
    }
}
class ACLItem{
    public long uid;
    public int R;
    public int W;
    public int E;
    ACLItem(long uid,int R,int W,int E){
        this.uid = uid;
        this.R = R;
        this.W = W;
        this.E = E;
    }
}