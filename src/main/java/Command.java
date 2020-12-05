import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Pattern;

@WebServlet(name = "cmd",urlPatterns = {"cmd"})
public class Command extends HttpServlet {
    private String cmd;
    private Disk disk = new Disk();
    private FCB fcb;
    private User user;
    private DirectoryItem current_dir;
    private DirectoryItem uroot;
    //用一个哈希映射表存储当前打开文件的文件名和文件描述符
    private HashMap<String,Integer> Ofmap = new HashMap<>();
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
        switch (cmd){
            case "dir":
                dir(req,resp);
                break;
            case "create":
                create(req,resp);
                break;
            case "delete":
                delete(req,resp);
                break;
            case "open":
                open(req,resp);
                break;
            case "close":
                close(req,resp);
                break;
            case "read":
                read(req,resp);
                break;
            case "write":
                write(req,resp);
                break;
        }
    }
    private void initDisk(){
        disk = new Disk();
    }
    private void createUser(int permission){
        User user = new User(permission);
        disk.sroot.dirs.add(user.uroot);
        disk.usertable.add(user);
    }
    private void login(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("name");
        String psw = req.getParameter("psw");
        PrintWriter out = resp.getWriter();
        for (User user: disk.usertable){
            if (user.name.equals(username)&&user.psw==psw){
                uroot = user.uroot;
                out.println("登录成功");
                return;
            }
        }
        out.println("登录失败");
    }
    private void dir(HttpServletRequest req, HttpServletResponse resp){
        String path = req.getParameter("path");

    }
    private boolean requestDist( int maxLength )
    {
        boolean flag = false;   // 标记是否分配成功
        int num = maxLength%Disk.MAXDISKBLOCK==0?maxLength/Disk.MAXDISKBLOCK:maxLength/Disk.MAXDISKBLOCK+1;      //算出需要几个磁盘块
        //判断当前空闲盘块数目是否足够
        if (num<=disk.diskBlockList.size()){
            int i=0;
            while (i<num){
                DiskBlockNode node = disk.diskBlockList.remove();
                fcb.flist.add(node);
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
    private DirectoryItem search(DirectoryItem dir,ArrayList<String> dirname,int level){
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
            return search(uroot,dirs,0);
        }else{
            return search(current_dir,dirs,0);
        }
    }
    private void create(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");
        String name = req.getParameter("name");
        PrintWriter out = resp.getWriter();
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
        //不重名，则开始分配空间
        if (requestDist(0)){        //如果分配空间成功
            fcb = new FCB();
            fcb.filename = name;
            fcb.creatTime = new Date().getTime();
            fcb.lastModifyTime = fcb.creatTime;
            fcb.size = 0;
            fcb.type = FileType.USER_FILE;
            fcb.usecount = 0;
            fcb.permission = 7;
            DirectoryItem item = new DirectoryItem();
            item.name = name;
            item.fcb = fcb;
            item.tag = 0;
            result.dirs.add(item);
        }else{
            out.println("内存已满，分配空间失败");
        }
    }
    private void delete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");
        String name = req.getParameter("name");
        PrintWriter out = resp.getWriter();

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
                if (user.permission>=item.fcb.permission){
                    recoverDist(item.fcb);
                }else{
                    out.println("您没有删除当前文件的权限");
                }
                break;
            }
        }
    }
    private void open(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PrintWriter out = resp.getWriter();
        String name = req.getParameter("name");
        String path = req.getParameter("path");
        DirectoryItem result = findPath(path);
        if (result==null){
            out.println("未找到指定路径");
            return;
        }
        //找到所在目录后，从目录项中读取该文件的信息，首先判断是否有打开权限
        for (DirectoryItem item:result.dirs) {
            if (item.name.equals(name)){
                if (item.fcb.permission>=user.permission){
                    //权限足够，可以打开,即将该文件的相关信息填入到用户进程打开文件表和系统进程打开文件表
                    UserOpenFile userOpenFile = new UserOpenFile();
                    userOpenFile.filename = name;
                    userOpenFile.rwlocation = 0;
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
                    Ofmap.put(name, userOpenFile.uid);
                }else{
                    out.println("您没有打开该文件的权限");
                }
            }
        }
        //如果有，将这些信息填入到系统打开文件表和用户进程打开文件表

    }
    private void close(HttpServletRequest req, HttpServletResponse resp){

    }
    private void read(HttpServletRequest req, HttpServletResponse resp){

    }
    private void write(HttpServletRequest req, HttpServletResponse resp){

    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
        resp.setContentType("text/html;charset=utf-8");
        cmd = req.getParameter("cmd");
        processCmd(req,resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp);
    }
}
