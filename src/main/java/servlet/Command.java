package servlet;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sun.mail.imap.ACL;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@WebServlet(name = "cmd", urlPatterns = {"/cmd"})
public class Command extends HttpServlet {
    public static Disk disk;
    public static List<User> usertable;                        //用户表
    public static Map<Long,SystemOpenFile> softable;          //系统打开文件表
    public static HashMap<Integer, String> buffer;
    public static int id = 0;
    public PrintWriter out;
    @Override
    public void init() {
        disk = new Disk();
        buffer = new HashMap<>();
        usertable = new ArrayList<>();
        softable = new HashMap<>();
        try {
            //创建一个用户
            createUser("li554","123456");
            //登录用户
            int uid1 = login("li554","123456");
            User user = usertable.get(uid1);
            //先创建一个文件夹
            user.mkdir("test1","/li554/");
            user.mkdir("test2","/li554/");
            user.mkdir("test3","/li554/");
            //试试通过相对路径创建一个目录
            user.mkdir("test11","li554/test1");
            //试试通过相对路径进入文件夹test11;
            user.cd("li554/test1/test11");
            //试试在test11文件夹下创建一个文件
            user.create("a.txt","");
            //试试以覆盖写的方式打开一个文件
            long uid = user.open("a.txt","",Mode.WRITE_FIRST);
            //试试向文件a.txt写入一段字符串
            user.write(uid,"hello world");
            //试试关闭一个文件
            user.close(uid);
            //重新以读的方式打开a.txt,并读出数据
            long uid2 = user.open("a.txt","",Mode.READ_ONLY);
            System.out.println(user.read(uid2));
            user.close(uid2);
            //再次以添加写的方式打开a.txt
            long uid3 = user.open("a.txt","",Mode.WRITE_APPEND);
            user.write(uid3,"you are very beautiful");
            user.close(uid3);
            //再次读一下a.txt文件
            long uid4 = user.open("a.txt","",Mode.READ_ONLY);
            System.out.println(user.read(uid4));
            user.close(uid4);
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    public String error(int n){
        switch (n){
            case Error.DUPLICATION:
                return "重名";
            case Error.DISK_OVERFLOW:
                return "磁盘已满，分配失败";
            case Error.PATH_NOT_FOUND:
                return "未找到指定路径";
            case Error.PERMISSION_DENIED:
                return "权限不足";
            case Error.USING_BY_OTHERS:
                return "文件被其他进程占用";
            case Error.UNKNOWN:
                return "未知错误";
        }
        return "ok";
    }

    public void createUser(String uname, String psw) throws IOException {
        //初始化用户基本信息
        User user = new User(uname, psw);
        //在用户表中添加用户
        usertable.add(user);
        //为用户创建文件夹
        int code = user.mkdir(uname,"/");
        if (code==Error.DUPLICATION){
            return;
        }
        //更新所有文件的访问控制表,增加新增用户对文件的权限信息
        for (Inode node1:disk.inodeMap.values()){
            if (node1.uid!=user.uid)
                node1.acl.put(user.uid,new ACLItem(user.uid,1,0,0));
        }
    }

    public int login(String username, String psw) throws IOException {
        int i=0;
        for (User user : usertable) {
            if (user.name.equals(username) && user.psw.equals(psw)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public JSONObject getDirectory(DirectoryItem root) throws UnsupportedEncodingException {
        /*
        一个文件对象，它有children和label两个属性，其中label为文件或目录名
        children表示该目录下的子项（文件或目录）
        {
            label:"",
            children:[{
                    label:"",
                },{
                    label:"",
                    children:[{
                        label:""
                    }]
                }]
        }
         */
        JSONObject object = new JSONObject();
        object.put("label",root.name);
        Inode node = disk.inodeMap.get(root.inodeid);
        if (node.type==FileType.FOLDER_FILE){
            JSONArray array = new JSONArray();
            for (DirectoryItem item:root.getDirs()){
                if (item.inodeid!=root.inodeid){
                    array.add(getDirectory(item));
                }
            }
            object.put("children",array);
        }
        return object;
    }
    /*
    public static void main(String[] args) throws IOException {
        Command command = new Command();
        //初始化
        command.initDisk();

        非指令测试集
        //创建一个用户
        command.createUser("li554","123456",Permission.RW_EXEC);
        //登录用户
        command.login("li554","123456");
        //先创建一个文件夹
        command.mkdir("test1","/li554/");
        command.mkdir("test2","/li554/");
        command.mkdir("test3","/li554/");
        //试试通过相对路径创建一个目录
        command.mkdir("test11","test1");
        //试试通过相对路径进入文件夹test11;
        command.cd("test1/test11");
        //试试在test11文件夹下创建一个文件
        command.create("a.txt","",Permission.RW_EXEC);
        //试试以覆盖写的方式打开一个文件
        int uid = command.open("a.txt","",Mode.WRITE_FIRST);
        //试试向文件a.txt写入一段字符串
        command.write(uid,"hello world");
        //试试关闭一个文件
        command.close(uid);
        //重新以读的方式打开a.txt,并读出数据
        int uid2 = command.open("a.txt","",Mode.READ_ONLY);
        System.out.println(command.read(uid2));
        command.close(uid2);
        //再次以添加写的方式打开a.txt
        int uid3 = command.open("a.txt","",Mode.WRITE_APPEND);
        command.write(uid3,"you are very beautiful");
        command.close(uid3);
        //再次读一下a.txt文件
        int uid4 = command.open("a.txt","",Mode.READ_ONLY);
        System.out.println(command.read(uid4));
        command.close(uid4);

        指令测试集
        reg li554 123455
        login li554 123456
        mkdir test1 /li554
        mkdir test11 test1
        create a.txt test1/test11
        open a.txt test1/test11 1
        write 0 hello world
        close 0
        open a.txt test1/test11 0
        read 0
        close 0
        open a.txt test1/test11 2
        write 0 you are beautiful
        close 0
        open a.txt test1/test11 0
        read 0
        close 0
        while (true){
            command.input();
        }
    }
*/
    /*
    public void input() throws IOException {
        Scanner input=new Scanner(System.in);
        String[] temp = input.nextLine().split("\\s+");
        String[] a = temp[0].equals("")?Arrays.copyOfRange(temp,1,temp.length):temp;
        String cmd = a[0];
        System.out.println(cmd);
        processCmd(cmd,a);
    }
    */
    public void processCmd(String[] a,HttpSession session) throws IOException {
        String cmd = a[0];
        Object temp = session.getAttribute("uid");
        int uid = temp==null?-1:(int)temp;
        switch (cmd) {
            case "reg": {
                String name = a[1];
                String psw = a[2];
                createUser(name, psw);
            }
            break;
            case "login": {
                String name = a[1];
                String psw = a[2];
                int uid1 = login(name, psw);
                int code = uid1>0?1:0;
                JSONObject object = new JSONObject();
                object.put("code",code);
                if (code==1) {
                    object.put("msg","登录成功");
                    session.setAttribute("uid",uid1);
                }
                else object.put("msg","未找到指定用户");
                out.println(object.toJSONString());
            }
            break;
            case "mkdir": {
                String name = a[1];
                String path = a[2];
                int code = usertable.get(uid).mkdir(name, path);
                JSONObject object = new JSONObject();
                object.put("code",code);
                object.put("msg",error(code));
                out.println(object.toJSONString());
            }
            break;
            case "rmdir": {
                String name = a[1];
                String path = a[2];
                int code = usertable.get(uid).rmdir(name, path);
                JSONObject object = new JSONObject();
                object.put("code",code);
                object.put("msg",error(code));
                out.println(object.toJSONString());
            }
            break;
            case "cd": {
                String path = a[1];
                int code = usertable.get(uid).cd(path);
                JSONObject object = new JSONObject();
                object.put("code",code);
                object.put("msg",error(code));
                out.println(object.toJSONString());
            }
            break;
            case "create": {
                String name = a[1];
                String path = a[2];
                int code = usertable.get(uid).create(name, path);
                JSONObject object = new JSONObject();
                object.put("code",code);
                object.put("msg",error(code));
                out.println(object.toJSONString());
            }
            break;
            case "delete": {
                String name = a[1];
                String path = a[2];
                int code = usertable.get(uid).delete(name, path);
                JSONObject object = new JSONObject();
                object.put("code",code);
                object.put("msg",error(code));
                out.println(object.toJSONString());
            }
            break;
            case "open": {
                String name = a[1];
                String path = a[2];
                int mode = Integer.parseInt(a[3]);
                long code = usertable.get(uid).open(name, path, mode);
                JSONObject object = new JSONObject();
                object.put("code",code);
                object.put("msg",error((int) code));
                out.println(object.toJSONString());
            }
            break;
            case "close": {
                Long uid1 = Long.parseLong(a[1]);
                usertable.get(uid).close(uid1);
            }
            break;
            case "read": {
                Long uid1 = Long.parseLong(a[1]);
                JSONObject object = usertable.get(uid).read(uid1);
                int code = object.getIntValue("code");
                object.put("msg",error(code));
                out.println(object.toJSONString());
            }
            break;
            case "write": {
                Long uid1 = Long.parseLong(a[1]);
                String content = a[2];
                System.out.println(content);
                int code = usertable.get(uid).write(uid1, content);
                JSONObject object = new JSONObject();
                object.put("code",code);
                object.put("msg",error(code));
                out.println(object.toJSONString());
            }
            break;
            case "copy": {
                String name = a[1];
                String path = a[2];
                JSONObject object = usertable.get(uid).copy(name, path);
                int code = object.getIntValue("code");
                object.put("msg",error(code));
                out.println(object.toJSONString());
            }
            break;
            case "paste": {
                String name = a[1];
                String path = a[2];
                int id = Integer.parseInt(a[3]);
                JSONObject object = new JSONObject();
                if (id==-1) {
                    object.put("code",-1);
                    object.put("msg","当前没有可粘贴文件");
                }else{
                    int code = usertable.get(uid).paste(name, path, id);
                    object.put("code",code);
                    object.put("msg",error(code));
                }
                out.println(object.toJSONString());
            }
            break;
            case "rename": {
                String name = a[1];
                String path = a[2];
                String newname = a[3];
                int code = usertable.get(uid).rename(name, path, newname);
                JSONObject object = new JSONObject();
                object.put("code",code);
                object.put("msg",error(code));
                out.println(object.toJSONString());
            }
            break;
            //后面是与前端数据显示相关的函数
            case "getDirectory": {
                getDirectory();
            }
            break;
            case "getTableData":{
                DirectoryItem root = usertable.get(uid).getParent(a[1]);
                getTableData(root);
            }
            break;
            case "getSearchData":{
                String q = a[1];
                int mode = Integer.parseInt(a[2]);
                out.println(usertable.get(uid).getSearchData(q,mode));
            }
            break;
            case "showProperty":{
                String name = a[1];
                String path = a[2];
                DirectoryItem result = usertable.get(uid).getParent(path);
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                for (DirectoryItem item:result.getDirs()){
                    Inode node = disk.inodeMap.get(item.inodeid);
                    if (item.name.equals(name)){
                        out.println("<div>名称:"+item.name+"</div>");
                        out.println("<div>类型:"+ (node.type==FileType.PLAIN_FILE?"文件":"文件夹")+"</div>");
                        out.println("<div>创建时间:"+simpleDateFormat.format(node.creatTime)+"</div>");
                        out.println("<div>修改时间:"+simpleDateFormat.format(node.lastModifyTime)+"</div>");
                        out.println("<div>路径:"+a[2]+"/"+a[1]+"</div>");
                        out.println("<div>大小:"+node.size+"</div>");
                    }
                }
            }
            break;
            case "showDisk":{
                JSONObject object = disk.showDisk();
                out.println("<div>磁盘空间"+object.get("all")+"</div>");
                out.println("<div>已用空间"+object.get("used")+"</div>");
                out.println("<div>可用空间"+object.get("left")+"</div>");
            }
            break;
        }
    }

    public void getDirectory() throws UnsupportedEncodingException {
        JSONObject object = getDirectory(disk.sroot);
        out.println(object.toJSONString());
    }

    public void getTableData(DirectoryItem root) throws UnsupportedEncodingException {
        JSONArray array = new JSONArray();
        if (root==null){
            JSONObject object = new JSONObject();
            object.put("filename",disk.sroot.name);
            Inode node = disk.inodeMap.get(disk.sroot.inodeid);
            object.put("modtime",node.creatTime);
            object.put("type","文件夹");
            object.put("size",node.size);
            array.add(0,object);
        }else{
            for (DirectoryItem item:root.getDirs()){
                if (root.inodeid!=item.inodeid){
                    JSONObject object = new JSONObject();
                    Inode node = disk.inodeMap.get(item.inodeid);
                    object.put("filename",item.name);
                    object.put("modtime",node.lastModifyTime);
                    object.put("type",node.type==FileType.PLAIN_FILE?"文件":"文件夹");
                    object.put("size",node.size);
                    array.add(0,object);
                }
            }
        }
        out.println(array.toJSONString());
    }

    public String[] toStringArray(JSONArray array) {
        if(array==null)
            return null;
        String[] arr=new String[array.size()];
        for(int i=0; i<arr.length; i++) {
            arr[i]=array.getString(i);
        }
        return arr;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=utf-8");
        InputStreamReader insr = new InputStreamReader(req.getInputStream(),"utf-8");
        String result = "";
        int respInt = insr.read();
        while(respInt!=-1) {
            result +=(char)respInt;
            respInt = insr.read();
        }
        JSONObject jsonRet = JSONObject.parseObject(result);
        out = resp.getWriter();
        JSONArray array = jsonRet.getJSONArray("param");
        String[] a = toStringArray(array);
        HttpSession session = req.getSession();
        processCmd(a,session);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
