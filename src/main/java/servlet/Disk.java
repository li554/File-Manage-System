package servlet;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static servlet.Command.*;

public class Disk {
    final static int DISK_SIZE = 512*1024;        //总大小
    LinkedList<DiskBlockNode> diskBlockList;      //空闲盘块链
    DirectoryItem sroot;                          //根目录
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
    
    synchronized public boolean requestDist(LinkedList<DiskBlockNode> list, int maxLength) {
        boolean flag = false;   // 标记是否分配成功
        int num = maxLength % Disk.DISK_SIZE == 0 ? maxLength / Disk.DISK_SIZE : maxLength / Disk.DISK_SIZE + 1;      //算出需要几个磁盘块
        //判断当前空闲盘块数目是否足够
        if (num <= diskBlockList.size()) {
            int i = 0;
            while (i < num) {
                DiskBlockNode node = diskBlockList.remove();
                list.add(node);
                i++;
            }
            flag = true;
        }
        return flag;
    }

    synchronized public void recoverDist(Inode inode) {
        while (!inode.flist.isEmpty()) {
            DiskBlockNode node = inode.flist.remove();
            diskBlockList.add(node);
        }
    }

    public JSONObject showDisk(){
        //返回磁盘的大小，剩余空间
        JSONObject object = new JSONObject();
        int size = inodeMap.get((long)0).size;
        object.put("all",Disk.DISK_SIZE);
        object.put("used",size);
        object.put("left",Disk.DISK_SIZE-size);
        return object;
    }
    
    public void initDisk(){
        for (Inode node:inodeMap.values()){
            recoverDist(node);
        }
        inodeMap.clear();
        Inode inode = new Inode(0,FileType.FOLDER_FILE,0);
        inodeMap.put((long) 0,inode);
    }
}

class DirectoryItem{            //目录索引项
    public String name;         //文件名
    public long inodeid;          //索引节点号
    DirectoryItem(String name,long inodeid){
        this.name = name;
        this.inodeid = inodeid;
    }
    public List<DirectoryItem> getDirs() throws UnsupportedEncodingException {
        if (inodeid==-1) {
            List<DirectoryItem> list = new ArrayList<>();
            list.add(disk.sroot);
            return list;
        }
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
        for (String s : a) {
            String[] b = s.split(",");
            DirectoryItem item = new DirectoryItem(b[0], Long.parseLong(b[1]));
            list.add(item);
        }
        return list;
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
    public Map<Long,UserOpenFile> uoftable;           //用户打开文件表
    public Map<Long,CLItem>visitList;
    public DirectoryItem current_dir;                 //用户当前目录
    User(String name,String psw){
        this.uid = new Date().getTime();
        this.name = name;
        this.psw = psw;
        this.uoftable = new HashMap<>();
        this.visitList = new HashMap<>();
        this.current_dir = disk.sroot;
    }

    public DirectoryItem getParent(String path) throws UnsupportedEncodingException {
        if (path.equals("./")) {
            return new DirectoryItem("",-1);
        }
        if (path!=null&&path.length() > 0) {
            char ch = path.charAt(0);
            //分割路径，得到各级目录名
            String[] temp = path.split("/");
            //删除空字符串，最终路径结果保留在dirs数组中
            List<String> dirs = new ArrayList<>();
            for (String dir : temp) {
                if (!dir.equals("")) {
                    dirs.add(dir);
                }
            }
            //根据第一个字符是否为/判断path为绝对路径还是相对路径
            if (ch == '/') {
                return recurSearch(disk.sroot, dirs, 0);
            } else {
                return recurSearch(this.current_dir, dirs, 0);
            }
        } else {
            return this.current_dir;
        }
    }
    
    public int mkdir(String name, String path) throws IOException {
        //首先检查路径是否存在
        DirectoryItem result = getParent(path);
        if (result == null) {
            return Error.PATH_NOT_FOUND;
        }
        //在获得的目录中查找是否已经存在该名字的文件，找到则提示重名错误
        for (DirectoryItem item : result.getDirs()) {
            if (item.name.equals(name)) {
                return Error.DUPLICATION;
            }
        }
        //检查是否有权限创建文件
        if (check_permission(result.inodeid,Mode.WRITE_APPEND)){
            long time =  new Date().getTime();
            Inode node = new Inode(this.uid, FileType.FOLDER_FILE,time);
            disk.inodeMap.put(time,node);
            //为该索引节点创建一个目录项,即将目录项信息写入父目录文件(文件名，索引号）
            //先打开父目录文件,路径应该修改删除掉当前名
            String fpath = path;
            if (path.equals("/")) fpath="./";
            long uid = open(result.name,fpath,Mode.WRITE_APPEND);
            String content = name+","+time+";";
            //然后写入文件信息
            write(uid,content);
            //最后关闭文件
            close(uid);
            //为文件对象初始化访问控制表
            for (User user:usertable){
                if (user.uid!=this.uid)
                    node.acl.put(user.uid,new ACLItem(user.uid,1,0,0));
                else
                    node.acl.put(user.uid,new ACLItem(user.uid,1,1,1));
            }
            //打开该目录文件,写入自身目录项
            long uid2 = open(name,path,Mode.WRITE_APPEND);
            content = name+","+time+";";
            write(uid2,content);
            close(uid2);
            return Success.MKDIR;
        }else{
            return Error.PERMISSION_DENIED;
        }
    }

    public int rename(String name, String path, String newname) throws IOException {
        //重命名也就是根据文件名和文件路径查找到对应的目录项，然后修改其名字即可
        //首先直接在系统打开文件表中查找该文件，获取打开计数器的值
        DirectoryItem result = getParent(path);
        long inodeid = -1;
        for (DirectoryItem item:result.getDirs()){
            if (item.inodeid!=result.inodeid&&item.name.equals(name)) {
                inodeid = item.inodeid;
                break;
            }
        }
        for (SystemOpenFile file : softable.values()) {
            if (file.fitem.inodeid==inodeid) {
                //如果两个文件的索引节点号相同，那么一定是同一个文件
                return Error.USING_BY_OTHERS;
            }
        }
        for (DirectoryItem item : result.getDirs()) {
            if (item.inodeid!=result.inodeid&&item.name.equals(newname)) {
                return Error.DUPLICATION;
            }
        }
        //在父目录中删除对应表项
        //先打开父目录文件
        String fpath = path;
        if (path.equals("/")) fpath="./";
        long uid = open(result.name,fpath,Mode.READ_WRITE);
        if (uid<0) return (int) uid;
        JSONObject object = read(uid);
        String content = (String) object.get("content");
        String substr = name+","+inodeid+";";
        System.out.println(substr);
        System.out.println(content);
        content = content.replace(substr,newname+","+inodeid+";");
        System.out.println(content);
        //然后写入文件信息
        int code = write(uid,content);
        if (code!=Success.WRITE) return code;
        //最后关闭文件
        close(uid);
        //删除一个对应的表项
        return Success.RENAME;
    }

    public void close(long uid) {
        //关闭文件,需要提供一个文件描述符,系统在用户进程中的打开文件表找到对应文件，然后删除该文件项
        //并且根据获得的sid查找系统打开文件表，如果系统打开文件表中对应表项的打开计数器的值>1那么-1，否则，删除表项
        try {
            UserOpenFile uitem = this.uoftable.get(uid);
            SystemOpenFile sitem = softable.get(uitem.sid);
            this.uoftable.remove(uid);
            if (sitem.opencount > 1) {
                sitem.opencount--;
            } else {
                softable.remove(sitem.sid);
            }
        } catch (IndexOutOfBoundsException e) {
            System.out.println("文件已经关闭，无需再次关闭");
        }
    }

    public JSONObject read(long uid) throws UnsupportedEncodingException {
        UserOpenFile uitem = this.uoftable.get(uid);
        JSONObject object = new JSONObject();
        object.put("code",0);
        object.put("content","");
        if (uitem.mode == Mode.WRITE_FIRST || uitem.mode == Mode.WRITE_APPEND) {
            object.replace("code",String.valueOf(Error.PERMISSION_DENIED));
        } else {
            SystemOpenFile sitem = softable.get(uitem.sid);
            if (check_permission(sitem.fitem.inodeid, Mode.READ_ONLY)){
                StringBuilder content = new StringBuilder();
                for (DiskBlockNode node : disk.inodeMap.get(sitem.fitem.inodeid).flist) {
                    String str = new String(node.content);
                    Pattern pattern = Pattern.compile("([^\u0000]*)");
                    Matcher matcher = pattern.matcher(str);
                    if(matcher.find(0)){
                        content.append(new String(matcher.group(1).getBytes("utf-8")));
                    }
                }
                object.replace("content",content.toString());
            }else{
                object.replace("code",String.valueOf(Error.PERMISSION_DENIED));
            }
        }
        return object;
    }

    public int write(long uid, String content) throws UnsupportedEncodingException {
        UserOpenFile uitem = this.uoftable.get(uid);
        if (uitem.mode == Mode.READ_ONLY) {
            return Error.PERMISSION_DENIED;
        } else {
            SystemOpenFile sitem = softable.get(uitem.sid);
            if (check_permission(sitem.fitem.inodeid, uitem.mode)){
                if (uitem.mode == Mode.WRITE_FIRST || uitem.mode==Mode.READ_WRITE) {
                    //如果是重写，那么先回收分配的磁盘块，然后重新根据大小赋予新的磁盘块
                    disk.recoverDist(disk.inodeMap.get(sitem.fitem.inodeid));
                    uitem.wpoint = 0;
                }else{
                    uitem.wpoint = disk.inodeMap.get(sitem.fitem.inodeid).size;
                }
                //现在已经获取到了该文件，通过前端传来的的文本值，我们将该值写入文件
                //中文字符占两个字节，其他字符为1个字节
                byte[] bytes = content.getBytes();
                //r为0表示所有分配的磁盘块都刚好占满
                int r = uitem.wpoint % DiskBlockNode.NODE_SIZE;
                //start表示在分配给该文件的磁盘块里面第一个没写入数据的磁盘块的下标
                int start = uitem.wpoint / DiskBlockNode.NODE_SIZE;
                if (r != 0||start<disk.inodeMap.get(sitem.fitem.inodeid).flist.size()-1)
                {
                    int k = 0;
                    for (int j=start;j<disk.inodeMap.get(sitem.fitem.inodeid).flist.size();j++){
                        DiskBlockNode node = disk.inodeMap.get(sitem.fitem.inodeid).flist.get(j);
                        if (j==start){
                            for (int i = 0; i < DiskBlockNode.NODE_SIZE- r && k < bytes.length; i++) {
                                node.content[r + i] = bytes[k++];
                                //修改读写指针的数值
                                uitem.wpoint++;
                            }
                        }else{
                            for (int i=0;i< DiskBlockNode.NODE_SIZE&& k< bytes.length;i++){
                                node.content[i] = bytes[k++];
                                uitem.wpoint++;
                            }
                        }
                    }
                    //去掉bytes数组的已经写入的部分数据
                    if (k < bytes.length)
                        bytes = Arrays.copyOfRange(bytes, k, bytes.length);
                    else
                        bytes = new byte[]{};
                }
                if (bytes.length > 0) {
                    //分配剩余的字节数据所需的磁盘块
                    if (!disk.requestDist(disk.inodeMap.get(sitem.fitem.inodeid).flist, bytes.length)) {
                        return Error.DISK_OVERFLOW;
                    }
                    //分配了空间之后，开始写入剩余数据
                    int i = 0;
                    for (DiskBlockNode node : disk.inodeMap.get(sitem.fitem.inodeid).flist) {
                        if (i >= start) {
                            node.content = Arrays.copyOfRange(bytes, (i - start) * DiskBlockNode.NODE_SIZE, (i - start + 1) * DiskBlockNode.NODE_SIZE);
                        }
                        i++;
                    }
                }
                //修改读写指针
                uitem.wpoint += bytes.length;
                //修改文件的size为写指针
                int origin_size = disk.inodeMap.get(sitem.fitem.inodeid).size;
                disk.inodeMap.get(sitem.fitem.inodeid).size = uitem.wpoint;
                updateSAT(disk.sroot,origin_size,sitem.fitem.inodeid);
            }else{
                return Error.PERMISSION_DENIED;
            }
        }
        return Success.WRITE;
    }

    synchronized public JSONObject copy(String name, String path) throws IOException {
        //复制文件其实就是拿到对应文件的inode中的flist,调用读函数读出flist中的内容
        //然后选定复制目录后，确定粘贴的时候，调用create命令,创建一个同名文件，大小为0，然后调用write写入数据，在写入中重新分配磁盘块
        //要想拿到inode,就需要根据当前文件名和文件路径在目录中查找到对应的文件，然后返回该文件的内容
        //当粘贴的时候再去执行创建，写入的工作
        long uid = open(name, path, Mode.READ_ONLY);
        JSONObject object = read(uid);
        object.put("bufferid",buffer.size());
        if (object.getIntValue("code")>=0)
            buffer.put(id++, object.getString("content"));
        close(uid);
        return object;
    }

    public int paste(String name, String path, int id) throws IOException {
        //调用create命令,创建一个同名文件，大小为0，然后调用write写入数据，在写入中重新分配磁盘块
        int code1,code2;
        code1 = create(name, path);
        if (code1==Success.CREATE){
            long uid = open(name, path, Mode.WRITE_FIRST);
            if (uid>0){
                code2 = write(uid, buffer.get(id));
                if (code2==Success.WRITE){
                    close(uid);
                    buffer.remove(id);
                    return Success.PASTE;
                }else{
                    return code2;
                }
            }else{
                return (int) uid;
            }
        }else{
            return code1;
        }
    }

    public void search(JSONArray array, String pattern, String path, DirectoryItem root) throws UnsupportedEncodingException {
        for (DirectoryItem item : root.getDirs()) {
            if (item.inodeid!=root.inodeid){
                Inode node = disk.inodeMap.get(item.inodeid);
                if (Pattern.matches(pattern, item.name)) {
                    JSONObject object = new JSONObject();
                    object.put("value",item.name+"   "+path);
                    object.put("name",item.name);
                    object.put("type",node.type==FileType.PLAIN_FILE?"文件":"文件夹");
                    object.put("path",path);
                    array.add(object);
                }
                if (item.getDirs()!=null)
                    search(array, pattern, path+"/"+item.name, item);
            }
        }
    }

    public int cd(String path) throws UnsupportedEncodingException {
        DirectoryItem result = getParent(path);
        if (result!=null) {
            this.current_dir = result;
            return Success.CD;
        } else{
            return Error.PATH_NOT_FOUND;
        }
    }

    public int rmdir(String name, String path) throws IOException {
        DirectoryItem result = getParent(path);
        if (result == null) {
            return Error.PATH_NOT_FOUND;
        }
        if (check_permission(result.inodeid,Mode.WRITE_APPEND)) {
            //在获得的目录中查找对应的目录
            for (DirectoryItem item : result.getDirs()) {
                if (item.name.equals(name)) {
                    rmdir(item,path);
                    //在父目录中删除对应表项
                    //先打开父目录文件
                    String fpath = path;
                    if (path.equals("/")) fpath="./";
                    //在父目录中删除其表项
                    long uid = open(result.name,fpath,Mode.READ_WRITE);
                    JSONObject object = read(uid);
                    String content = (String) object.get("content");
                    String delstr = name+","+item.inodeid+";";
                    content = content.replace(delstr,"");
                    //然后写入文件信息
                    write(uid,content);
                    //最后关闭文件
                    close(uid);
                    return Success.RMDIR;
                }
            }
        }else{
            return Error.PERMISSION_DENIED;
        }
        return Error.UNKNOWN;
    }

    public int create(String name, String path) throws IOException {
        //首先检查是否重名
        DirectoryItem result = getParent(path);
        if (result == null) {
            return Error.PATH_NOT_FOUND;
        }
        //在获得的目录中查找是否已经存在该名字的文件，找到则提示重名错误
        for (DirectoryItem item : result.getDirs()) {
            if (item.name.equals(name)) {
                return Error.DUPLICATION;
            }
        }
        LinkedList<DiskBlockNode> list = new LinkedList<>();
        //判断是否有修改目录的权限
        if (check_permission(result.inodeid,Mode.WRITE_APPEND)){
            //不重名，则开始分配空间
            if (disk.requestDist(list, 0)) {
                //如果分配空间成功

                //创建一个索引节点
                Inode inode = new Inode(this.uid, FileType.PLAIN_FILE,new Date().getTime());
                //将索引节点的盘块链指向已分配盘块
                inode.flist = list;
                //将索引节点添加到索引节点目录中
                disk.inodeMap.put(inode.creatTime,inode);

                //为该索引节点创建一个目录项,即将目录项信息写入父目录文件(文件名，索引号）
                //先打开父目录文件
                String fpath = path;
                if (path.equals("/")) fpath="./";
                long uid = open(result.name,path,Mode.WRITE_APPEND);
                String content = name+","+inode.creatTime+";";
                //然后写入文件信息
                write(uid,content);
                //最后关闭文件
                close(uid);
                //为文件对象初始化访问控制表
                for (User user:usertable){
                    if (user.uid!=this.uid)
                        inode.acl.put(user.uid,new ACLItem(user.uid,1,0,0));
                    else
                        inode.acl.put(user.uid,new ACLItem(user.uid,1,1,1));
                }
                return Success.CREATE;
            } else {
                return Error.DISK_OVERFLOW;
            }
        }
        else{
            return Error.PERMISSION_DENIED;
        }
    }

    public boolean updateSAT(DirectoryItem root,int size,long inodeid) throws UnsupportedEncodingException{
    //        if (disk.inodeMap.get(root.inodeid).type==FileType.FOLDER_FILE){
//            int size = 0;
//            long time = 0;
//            for (DirectoryItem item:root.getDirs()){
//                if (item.inodeid!= root.inodeid){
//                    JSONObject object = updateSAT(item);
//                    size+=(int) object.get("size");
//                    if (time<(long) object.get("time")){
//                        time=(long) object.get("time");
//                    }
//                }
//            }
//            JSONObject object = new JSONObject();
//            object.put("size",size);
//            object.put("time",time);
//            disk.inodeMap.get(root.inodeid).size = size;
//            disk.inodeMap.get(root.inodeid).lastModifyTime = time;
//            return object;
//        }else{
//            JSONObject object = new JSONObject();
//            object.put("size",disk.inodeMap.get(root.inodeid).size);
//            object.put("time",disk.inodeMap.get(root.inodeid).lastModifyTime);
//            return object;
//        }
        if (disk.inodeMap.get(root.inodeid).type==FileType.PLAIN_FILE) {
            return root.inodeid==inodeid;
        }
        for (DirectoryItem item: root.getDirs()){//在所有子项中找是否包含该inode
            if (item.inodeid!=root.inodeid){
                if (updateSAT(item,size,inodeid)){   //如果某个子项包含
                    //更新当前目录的大小和修改时间
                    disk.inodeMap.get(root.inodeid).size+=disk.inodeMap.get(inodeid).size-size;
                    disk.inodeMap.get(root.inodeid).lastModifyTime+=disk.inodeMap.get(inodeid).lastModifyTime;
                }
            }
        }
        return false;
    }

    public int delete(String name, String path) throws IOException {
        //首先直接在系统打开文件表中查找该文件，获取打开计数器的值
        for (SystemOpenFile file : softable.values()) {
            if (file.fitem.name.equals(name) && file.opencount > 0) {
                return Error.USING_BY_OTHERS;
            }
        }
        DirectoryItem result = getParent(path);
        if (result == null) {
            return Error.PATH_NOT_FOUND;
        }
        for (DirectoryItem item : result.getDirs()) {
            if (item.name.equals(name)) {
                //没有被占用的话，判断一下用户是否有删除文件的权限，即是否有修改父目录文件的权限
                if (check_permission(result.inodeid,Mode.WRITE_FIRST)){
                    return delete(item, result,path);
                }else{
                    return Error.PERMISSION_DENIED;
                }
            }
        }
        return Error.UNKNOWN;
    }

    public long open(String name, String path, int mode) throws IOException {     //传入的是一个引用对象
        DirectoryItem result = getParent(path);
        if (result == null) {
            return Error.PATH_NOT_FOUND;
        }
        //找到所在目录后，从目录项中读取该文件的信息，首先判断是否有打开权限
        for (DirectoryItem item : result.getDirs()) {
            if (item.name.equals(name)) {
                //权限足够，接着检查是否当前进程已经打开了该文件
                for (UserOpenFile uof : this.uoftable.values()) {
                    if (uof.filename.equals(name)) {
                        return uof.uid;
                    }
                }
                UserOpenFile userOpenFile = new UserOpenFile();
                userOpenFile.filename = name;
                userOpenFile.mode = mode;
                userOpenFile.rpoint = 0;
                userOpenFile.uid = new Date().getTime();
                userOpenFile.sid = userOpenFile.uid;
                this.uoftable.put(userOpenFile.uid,userOpenFile);
                SystemOpenFile systemOpenFile = new SystemOpenFile();
                systemOpenFile.sid = userOpenFile.uid;
                systemOpenFile.filename = name;
                //打开计数器增加 1
                systemOpenFile.opencount += 1;
                //将该文件的目录项指针存在系统打开文件表中
                systemOpenFile.fitem = item;
                softable.put(systemOpenFile.sid,systemOpenFile);
                //最终应该返回一个文件描述符，也就是该文件在用户进程打开文件表中的uid
                return userOpenFile.uid;
            }
        }
        return Error.UNKNOWN;
    }

    public int delete(DirectoryItem file, DirectoryItem parent,String path) throws IOException {
        disk.recoverDist(disk.inodeMap.get(file.inodeid));
        //在父目录中删除对应表项
        //先打开父目录文件
        String fpath = path;
        if (path.equals("/")) fpath="./";
        long uid = open(parent.name,fpath,Mode.READ_WRITE);
        if (uid<0) return (int)uid;
        JSONObject object = read(uid);
        String content = (String) object.get("content");
        String delstr = file.name+","+file.inodeid+";";
        content = content.replace(delstr,"");
        //然后写入文件信息
        int code = write(uid,content);
        if (code!=Success.WRITE) return code;
        //最后关闭文件
        close(uid);
        disk.inodeMap.remove(file.inodeid);
        //删除对该文件的权限
        this.visitList.remove(file.inodeid);
        return Success.DELETE;
    }

    public boolean check_permission(long inodeid, int mode) {
        //检查权限需要知道当前的操作和当前操作的文件
        Map<Long,ACLItem> map= disk.inodeMap.get(inodeid).acl;
        //首先查询访问控制表，如果访问控制表没有对应的用户的权限信息，则从访问权限表中读出权限信息
        if (map.containsKey(this.uid)){
            ACLItem item = map.get(this.uid);
            int permission = item.R+item.W*2+item.E*4;
            switch (permission){
                case Permission.READ_ONLY:
                    if (mode != Mode.READ_ONLY) {
                        return false;
                    }
                    break;
                case Permission.WRITE_ONLY:
                    if (mode!= Mode.WRITE_FIRST&&mode!=Mode.WRITE_APPEND){
                        return false;
                    }
                    break;
            }
        }
        return true;
    }

    public String getSearchData(String q,int mode) throws IOException {
        //层序遍历
        //确定全局搜索还是当前文件夹
        //全局搜索
        String pattern = ".*"+q+".*";
        JSONArray array = new JSONArray();
        String path = "";
        if (mode==0){
            search(array,pattern,path,disk.sroot);
        }else{
            search(array,pattern,path,this.current_dir);
        }
        return array.toJSONString();
    }

    public DirectoryItem recurSearch(DirectoryItem dir, List<String> dirname, int level) throws UnsupportedEncodingException {
        if (level >= dirname.size()) {
            return dir;
        }
        for (DirectoryItem item : dir.getDirs()) {
            if (item.name.equals(dirname.get(level)) && item.getDirs() != null) {
                return recurSearch(item, dirname, level + 1);
            }
        }
        return null;
    }

    public void rmdir(DirectoryItem dir,String path) throws IOException {
        //在索引节点中删除目录
        for (DirectoryItem sitem : dir.getDirs()) {
            if (sitem.inodeid!=dir.inodeid){
                if (disk.inodeMap.get(sitem.inodeid).type == FileType.PLAIN_FILE) {
                    delete(sitem.name,path);
                } else {
                    rmdir(sitem,path);
                }
            }
        }
        disk.inodeMap.remove(dir.inodeid);
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