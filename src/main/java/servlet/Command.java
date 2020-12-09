package servlet;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.regex.Pattern;

@WebServlet(name = "cmd",urlPatterns = {"cmd"})
public class Command extends HttpServlet {
    private String cmd;
    private Disk disk = new Disk();
    private User current_user;
    private DirectoryItem current_dir;
    private PrintWriter out;
    /*
        dir 列文件目录
        create 创建文件
        delete 删除文件
        open 打开文件
        close 关闭文件
        read 读文件
        write 写文件
     */
    private void processCmd(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String name = req.getParameter("name");
        String path = req.getParameter("path");
        int uid = Integer.parseInt(req.getParameter("descriptor"));
        switch (cmd){
            case "dir":
                dir(name,path);
                break;
            case "create":
                create(name,path,7);
                break;
            case "delete":
                delete(name,path);
                break;
            case "open":
                int mode = Integer.parseInt(req.getParameter("mode"));
                uid = open(name,path,mode);
                out.println(uid);
                break;
            case "close":
                close(uid);
                break;
            case "read":
                read(uid);
                break;
            case "write":
                String content = req.getParameter("content");
                write(uid,content);
                break;
        }
    }
    private void initDisk(){
        disk = new Disk();
    }
    private void createUser(String uname,String psw,int permission){
        User user = new User(uname,psw,permission);
        disk.sroot.dirs.add(user.uroot);
        disk.usertable.add(user);
    }
    private void login(String username,String psw) throws IOException {
        for (User user: disk.usertable){
            if (user.name.equals(username)&&user.psw.equals(psw)){
                current_user = user;
                current_dir = user.uroot;
                out.println("登录成功");
                return;
            }
        }
        out.println("登录失败");
    }
    private void dir(String name,String path){

    }
    synchronized private boolean requestDist(LinkedList<DiskBlockNode> list, int maxLength )
    {
        boolean flag = false;   // 标记是否分配成功
        int num = maxLength% Disk.MAXDISKBLOCK==0?maxLength/ Disk.MAXDISKBLOCK:maxLength/ Disk.MAXDISKBLOCK+1;      //算出需要几个磁盘块
        //判断当前空闲盘块数目是否足够
        if (num<=disk.diskBlockList.size()){
            int i=0;
            while (i<num){
                DiskBlockNode node = disk.diskBlockList.remove();
                list.add(node);
                i++;
            }
            flag = true;
        }
        return flag;
    }
    private void recoverDist(FCB fcb){
        while (!fcb.flist.isEmpty()){
            DiskBlockNode node = fcb.flist.remove();
            disk.diskBlockList.add(node);
        }
    }
    private DirectoryItem search(DirectoryItem dir, ArrayList<String> dirname, int level){
        if (level>=dirname.size()){
            return dir;
        }
        for (DirectoryItem item:dir.dirs){
            if (item.name.equals(dirname.get(level))&&item.dirs!=null){
                return search(item,dirname,level+1);
            }
        }
        return null;
    }
    private void cd(String path){
        current_dir = findPath(path);
    }
    private DirectoryItem findPath(String path){
        //首先判断路径的合法性
        String pattern = "([a-zA-Z]:)?(\\\\[a-zA-Z0-9_.-]+)+\\\\?";
        if (!Pattern.matches(pattern,path)){
            return null;
        }
        char ch = path.charAt(0);
        //分割路径，得到各级目录名
        String[] temp = path.split("/");
        //删除空字符串，最终路径结果保留在dirs数组中
        ArrayList<String> dirs = new ArrayList<>();
        for (String dir:temp){
            if (!dir.equals("")){
                dirs.add(dir);
            }
        }
        //根据第一个字符是否为/判断path为绝对路径还是相对路径
        if (ch=='/') {
            return search(current_user.uroot,dirs,0);
        }else{
            return search(current_dir,dirs,0);
        }
    }
    private void create(String name,String path,int permission) throws IOException {
        //首先检查是否重名
        DirectoryItem result = findPath(path);
        if (result==null){
            out.println("未找到指定路径");
            return;
        }
        //在获得的目录中查找是否已经存在该名字的文件，找到则提示重名错误
        for (DirectoryItem item:result.dirs) {
            if (item.name.equals(name)){
                //发生重名
                out.println("创建失败，存在重名文件");
                return;
            }
        }
        LinkedList<DiskBlockNode> list = new LinkedList<>();
        //不重名，则开始分配空间
        if (requestDist(list,0)){        //如果分配空间成功
            FCB fcb = new FCB();
            fcb.flist = list;
            fcb.filename = name;
            fcb.creatTime = new Date().getTime();
            fcb.lastModifyTime = fcb.creatTime;
            fcb.size = 0;
            fcb.type = FileType.USER_FILE;
            fcb.usecount = 0;
            fcb.permission = permission;
            DirectoryItem item = new DirectoryItem();
            item.name = name;
            item.fcb = fcb;
            item.tag = 0;
            result.dirs.add(item);
        }else{
            out.println("内存已满，分配空间失败");
        }
    }
    private void delete(String name,String path) throws IOException {
        //首先直接在系统打开文件表中查找该文件，获取打开计数器的值
        for (SystemOpenFile file:disk.softable){
            if (file.filename.equals(name)&&file.opencount>0){
                out.println("存在进程正在使用该文件,无法删除");
                return;
            }
        }
        DirectoryItem result = findPath(path);
        if (result==null){
            out.println("未找到指定路径");
            return;
        }
        for (DirectoryItem item:result.dirs) {
            if (item.name.equals(name)){
                //没有被占用的话，判断一下用户是否有删除文件的权限
                if (check_permission(item.permission, -1)){
                    recoverDist(item.fcb);
                }else{
                    out.println("您没有删除当前文件的权限");
                }
                break;
            }
        }
    }
    private int open(String name,String path,int mode) throws IOException {     //传入的是一个引用对象
        DirectoryItem result = findPath(path);
        if (result==null){
            out.println("未找到指定路径");
            return -1;
        }
        //找到所在目录后，从目录项中读取该文件的信息，首先判断是否有打开权限
        for (DirectoryItem item:result.dirs) {
            if (item.name.equals(name)){
                if (check_permission(item.permission,mode)){
                    //权限足够，接着检查是否当前进程已经打开了该文件
                    for (UserOpenFile uof: disk.uoftable) {
                        if (uof.filename.equals(name)){
                            //表示该文件已经被打开，那么需要判断一下打开模式是否是重写
                            if (mode==Mode.WRITE_FIRST) {
                                //如果是重写，那么先回收分配的磁盘块，然后重新根据大小赋予新的磁盘块
                                recoverDist(item.fcb);
                                uof.wpoint = 0;
                                uof.mode = Mode.WRITE_FIRST;
                            }
                            return uof.uid;
                        }
                    }
                    //没有打开，那么将该文件的相关信息填入到用户进程打开文件表和系统进程打开文件表
                    UserOpenFile userOpenFile = new UserOpenFile();
                    userOpenFile.filename = name;
                    userOpenFile.mode = mode;
                    if (mode==Mode.WRITE_FIRST) {
                        //如果是重写，那么先回收分配的磁盘块，然后重新根据大小赋予新的磁盘块
                        recoverDist(item.fcb);
                        userOpenFile.wpoint = 0;
                    }
                    userOpenFile.rpoint = 0;
                    userOpenFile.uid = disk.uoftable.size();
                    userOpenFile.sid = disk.softable.size();
                    disk.uoftable.add(userOpenFile);

                    SystemOpenFile systemOpenFile = new SystemOpenFile();
                    systemOpenFile.sid = disk.softable.size();
                    systemOpenFile.filename = name;
                    //打开计数器增加 1
                    systemOpenFile.opencount += 1;
                    //将该文件的目录项指针存在系统打开文件表中
                    systemOpenFile.fitem = item;
                    disk.softable.add(systemOpenFile);
                    //最终应该返回一个文件描述符，也就是该文件在用户进程打开文件表中的uid
                    return userOpenFile.uid;
                }else{
                    out.println("您没有打开该文件的权限");
                }
                break;
            }
        }
        return -1;
    }
    private void close(int uid){
        //关闭文件,需要提供一个文件描述符,系统在用户进程中的打开文件表找到对应文件，然后删除该文件项
        //并且根据获得的sid查找系统打开文件表，如果系统打开文件表中对应表项的打开计数器的值>1那么-1，否则，删除表项
        UserOpenFile uitem = disk.uoftable.get(uid);
        SystemOpenFile sitem = disk.softable.get(uitem.sid);

        //每关闭一个文件，都需要对所有的文件重新编号
        for (UserOpenFile item:disk.uoftable){
            if (item.uid>uid){
                item.uid--;
            }
        }
        disk.uoftable.remove(uid);
        if (sitem.opencount>1){
            sitem.opencount--;
        }else{
            for (UserOpenFile item:disk.uoftable){
                if (item.sid>uitem.sid){
                    item.sid--;
                }
            }
            for (SystemOpenFile item: disk.softable){
                if (item.sid>uitem.sid){
                    item.sid--;
                }
            }
            disk.softable.remove(sitem.sid);
        }
    }
    private String read(int uid){
        UserOpenFile uitem = disk.uoftable.get(uid);
        if (uitem.mode==Mode.WRITE_FIRST){
            out.println("当前为只写模式");
            return null;
        }else{
            SystemOpenFile sitem = disk.softable.get(uitem.sid);
            StringBuilder content = new StringBuilder();
            for (DiskBlockNode node:sitem.fitem.fcb.flist){
                content.append(new String(node.content));
            }
            return content.toString();
        }
    }
    private void write(int uid,String content){
        UserOpenFile uitem = disk.uoftable.get(uid);
        if (uitem.mode==Mode.READ_ONLY){
            out.println("当前为只读模式模式");
            return;
        }else{
            SystemOpenFile sitem = disk.softable.get(uitem.sid);
            //现在已经获取到了该文件，通过前端传来的的文本值，我们将该值写入文件
            //中文字符占两个字节，其他字符为1个字节
            byte[] bytes = content.getBytes();
            //r为0表示所有分配的磁盘块都刚好占满
            int r = uitem.wpoint % Disk.MAXDISKBLOCK;
            if (r!=0){
                //r表示最后一个磁盘块中已经写入数据的大小，如果不为0，我们还需向该磁盘块写入maxdiskblock-r的字节数据才能占满它
                for (int i = 0; i< Disk.MAXDISKBLOCK-r; i++){
                    sitem.fitem.fcb.flist.getLast().content[r+i] = bytes[i];
                }
                //修改读写指针的数值
                uitem.wpoint += Disk.MAXDISKBLOCK-r;
                //去掉bytes数组的已经写入的部分数据
                bytes = Arrays.copyOfRange(bytes, Disk.MAXDISKBLOCK-r,bytes.length);
            }
            //分配剩余的字节数据所需的磁盘块
            if (!requestDist(sitem.fitem.fcb.flist,bytes.length)){
                out.println("存储空间已满，分配失败");
                return;
            }
            //分配了空间之后，开始写入剩余数据
            int i=0;
            //start表示在分配给该文件的磁盘块里面第一个没写入数据的磁盘块的下标
            int start = uitem.wpoint/ Disk.MAXDISKBLOCK;
            for (DiskBlockNode node:sitem.fitem.fcb.flist){
                if (i>=start){      
                    node.content = Arrays.copyOfRange(bytes,(i-start)*node.maxlength,(i-start+1)*node.maxlength);
                }
                i++;
            }
            //修改读写指针
            uitem.wpoint += bytes.length;
        }
    }
    private boolean check_permission(int permission, int mode){
        switch (current_user.permission){
            case Permission.READ_ONLY:
                if (mode!=Mode.READ_ONLY){
                    out.println("该用户为只读权限，不能对文件进行修改");
                    return false;
                }
                break;
            case Permission.READ_WRITE:
            case Permission.RW_EXEC:
                if (mode==Mode.READ_ONLY){
                    switch (permission){
                        case Permission.READ_WRITE:break;
                        case Permission.READ_EXEC:break;
                        case Permission.READ_ONLY:break;
                        case Permission.RW_EXEC:break;
                        default:
                            out.println("文件对用户为只读权限");return false;
                    }
                }else if (mode==Mode.WRITE_APPEND||mode==Mode.WRITE_FIRST){
                    switch (permission){
                        case Permission.READ_WRITE:break;
                        case Permission.WRITE_EXEC:break;
                        case Permission.WRITE_ONLY:break;
                        case Permission.RW_EXEC:break;
                        default:
                            out.println("文件对用户为只写权限");
                            return false;
                    }
                }else if (mode==Mode.READ_WRITE){
                    switch (permission){
                        case Permission.READ_WRITE:break;
                        case Permission.RW_EXEC:break;
                        default:
                            out.println("该文件对用户并不具有可读加可写的权限");
                            return false;
                    }
                }
                break;
        }
        return true;
    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
        resp.setContentType("text/html;charset=utf-8");
        out = resp.getWriter();
        cmd = req.getParameter("cmd");
        processCmd(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }

    public static void main(String[] args) throws IOException {
        Command command = new Command();
        command.createUser("li554","123456",7);
        command.login("li554","123456");
    }
}
