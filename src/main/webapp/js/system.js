var app = new Vue({
    el: "#app",
    data: {
        data: [],
        defaultProps: {
            children: 'children',
            label: 'label'
        },
        path: "",
        paths: ["/"],
        tableData: [{
            filename: "a.txt",
            modtime: "2018-1-12",
            type: "文件",
            size: 10
        }],
        inputlist: {
            input_show: false,
            filepath: "",
            filename:"",
            create:"",
            rename:"",
            mkdir:""
        },
        createDialogVisible:false,
        renameDialogVisible:false,
        folderDialogVisible:false,
        formLabelWidth:"120px",
        card: {
            card_show: false,
            contextmenu: ["打开文件", "删除文件", "复制", "粘贴", "重命名", "查看属性", "创建文件", "创建文件夹", "删除文件夹"],
            left: 0,
            top: 0
        },
        blankcard: {
            card_show: false,
            contextmenu: ["粘贴", "创建文件", "创建文件夹"],
            left: 0,
            top: 0
        },
        copy: {
            bufferid: -1
        },
        Success: {
            WRITE: -7,
            DELETE: -8,
            CREATE: -9,
            READ: -10,
            RMDIR: -11,
            MKDIR: -12,
            PASTE: -13,
            RENAME: -14,
            CD:-15
        },
        tab:{
            tabShow:false,
        },
        editableTabsValue: "",
        editableTabs: [],
        tempfilename:"",
    },
    created: function () {
        var that = this;
        axios.post('/cmd', {
            param: ["cd", "/"]
        })
            .then(function (response) {
                console.log(response.data);
                that.getDirectory();
                setTimeout(function () {
                    that.getTableData("");
                },100);
            })
            .catch(function (error) {
                console.log(error);
            })
        $(document).contextmenu(function () {
            that.blankcard.card_show = !that.blankcard.card_show;
            if (that.blankcard.card_show) {
                that.card.card_show = false;
                $(".blank-card").css({
                    "left": (window.scrollX + event.x + 10) + "px",
                    "top": (window.scrollY + event.y + 10) + "px"
                })
            }
            return false;
        })
    },
    methods: {
        handleNodeClick(data) {
            console.log(data);
        },
        handleCurrentChange(val) {
            this.currentRow = val;
        },
        handleRowClick(row, column, event) {
            var that = this;
            console.log(this.tableData);
            console.log(row);
            if (row.type == "文件夹") {
                this.paths.push(row.filename);
                axios.post('/cmd', {
                    param: ["cd", "/" + this.paths.join("/")]
                })
                    .then(function (response) {
                        console.log(response.data);
                        var object = response.data;
                        if (object.code == that.Success.CD)
                            that.getTableData("");
                        else {
                            that.$message(object.msg);
                            console.log(object.msg);
                        }
                    })
                    .catch(function (error) {
                        console.log(error);
                    })
            }
        },
        getDirectory(){
            var that = this;
            axios.post('/cmd', {
                param: ["getDirectory"]
            })
                .then(function (response) {
                    console.log(response.data);
                    that.data = [];
                    for (let i = 0; i < response.data.children.length; i++) {
                        that.data.push(response.data.children[i]);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                })
        },
        writefile(uid,content) {
            var that = this;
            axios.post('/cmd', {
                param: ["write",uid,content]
            })
                .then(function (response) {
                    var object = response.data;
                    if (object.code == that.Success.CD)
                        that.$message('保存成功');
                    else {
                        that.$message(object.msg);
                        console.log(object.msg);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                })
        },
        closefile(uid) {
            var that = this;
            axios.post('/cmd', {
                param: ["close",uid]
            })
                .then(function (response) {
                    console.log(response.data);
                    that.getTableData("");
                })
                .catch(function (error) {
                    console.log(error);
                })
        },
        back: function (path) {
            console.log(path);
            var that = this;
            while (this.paths.pop() != path) {
                continue;
            }
            this.paths.push(path);
            console.log(this.paths);
            console.log(this.paths.join("/"));
            axios.post('/cmd', {
                param: ["cd", "/" + this.paths.join("/")]
            })
                .then(function (response) {
                    console.log(response.data);
                    var object = response.data;
                    if (object.code == that.Success.CD)
                        that.getTableData("");
                    else {
                        that.$message(object.msg);
                        console.log(object.msg);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                })
        },
        handleContextMenuClick(row, column, event) {
            this.card.card_show = true;
            this.blankcard.card_show = true;
            $(".card").css({
                "left": (window.scrollX + event.x + 10) + "px",
                "top": (window.scrollY + event.y + 10) + "px"
            })
            this.tempfilename = row.filename;
        },
        openfile: function () {
            console.log("open file");
            var that = this;
            axios.post('/cmd', {
                param: ["open", that.tempfilename, "", 3]
            })
                .then(function (response) {
                    console.log(response);
                    var object = response.data;
                    if (object.code < 0) {
                        console.log(object.msg);
                        that.$message(object.msg);
                    } else {
                        axios.post('/cmd', {
                            param: ["read", object.code]
                        })
                            .then(function (response) {
                                console.log(response);
                                var object2 = response.data;
                                if (object2.code == 0) {
                                    that.addTab({
                                        uid: object.code+"",
                                        filename: that.tempfilename,
                                        filecontent: object2.content
                                    });
                                    that.tab.tabShow = true;
                                } else {
                                    that.$message(object2.msg);
                                    console.log(object2);
                                }
                            })
                            .catch(function (error) {
                                console.log(error);
                            });
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        deletefile: function () {
            var that = this;
            console.log("delete file");
            axios.post('/cmd', {
                param: ["delete", that.tempfilename, ""]
            })
                .then(function (response) {
                    var object2 = response.data;
                    if (object2.code == 0) {
                        that.getTableData("");
                        setTimeout(function (){
                            that.getDirectory();
                        },100);
                    } else {
                        that.$message(object2.msg);
                        console.log(object2);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        copyfile: function () {
            console.log("copy file");
            var that = this;
            //复制文件其实就是拿到对应文件的fcb中的flist,调用读函数读出flist中的内容
            //然后选定复制目录后，确定粘贴的时候，调用create命令,创建一个同名文件，大小为0，然后调用write写入数据，在写入中重新分配磁盘块
            //要想拿到fcb,就需要根据当前文件名和文件路径在目录中查找到对应的文件，然后返回该文件的内容
            //当粘贴的时候再去执行创建，写入的工作
            axios.post('/cmd', {
                param: ["copy", that.tempfilename, ""]
            })
                .then(function (response) {
                    console.log(response);
                    var object = response.data;
                    if (object.code < 0) {
                        console.log(object.msg);
                        that.$message(object.msg);
                    } else {
                        that.$message("复制成功");
                        that.copy.bufferid = object.bufferid;
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        pastefile: function () {
            console.log("paste file");
            var that = this;
            axios.post('/cmd', {
                param: ["paste", that.tempfilename, "", that.copy.bufferid]
            })
                .then(function (response) {
                    console.log(response);
                    var object = response.data;
                    if (object.code < 0) {
                        console.log(object.msg);
                        that.$message(object.msg);
                    } else {
                        that.$message("粘贴成功");
                        that.getTableData("");
                        setTimeout(function (){
                            that.getDirectory();
                        },100);;
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        renamefile: function () {
            //重命名也就是根据文件名和文件路径查找到对应的目录项，然后修改其名字即可
            //如果当前该文件已被打开，则不允许重命名
            console.log("rename file");
            var that = this;
            axios.post('/cmd', {
                param: ["rename", that.tempfilename, "", that.inputlist.rename]
            })
                .then(function (response) {
                    var obj = response.data;
                    if (obj.code != that.Success.RENAME) {
                        console.log(obj.msg);
                        that.$message(obj.msg);
                    } else {
                        that.getTableData("");
                        setTimeout(function (){
                            that.getDirectory();
                        },100);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        showfile: function () {
            var that = this;
            console.log("show fileProperty");
            //根据文件名和文件路径查找到对应的目录项和文件fcb，读取文件名和其它所有的属性的值，以json的格式返回文件属性信息
            axios.post('/cmd', {
                param: ["showProperty", that.tempfilename, that.paths.join("/").slice(1)]
            })
                .then(function (response) {
                    that.$alert(response.data, '属性', {
                        dangerouslyUseHTMLString: true
                    });
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        createfile: function () {
            console.log("create file");
            var that = this;
            axios.post('/cmd', {
                param: ["create", that.inputlist.create, ""]
            })
                .then(function (response) {
                    var object = response.data;
                    if (object.code == that.Success.CREATE){
                        that.getTableData("");
                        setTimeout(function (){
                            that.getDirectory();
                        },100);;
                    }else {
                        that.$message(object.msg);
                        console.log(object.msg);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        mkdir: function () {
            console.log("create directory");
            var that = this;
            axios.post('/cmd', {
                param: ["mkdir", that.inputlist.mkdir, ""]
            })
                .then(function (response) {
                    var object = response.data;
                    if (object.code == that.Success.MKDIR){
                        that.getTableData("");
                        setTimeout(function (){
                            that.getDirectory();
                        },100);
                    }else {
                        that.$message(object.msg);
                        console.log(object.msg);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        rmdir: function () {
            //删除目录
            //根据目录名和路径查找到对应的目录项
            //从目录项中找到所有的子项，如果子项是文件，调用删除文件的函数，
            //如果子项是目录，那么递归调用rmdir函数，删除目录，删除完所有的子项时结束
            //最后再删除该目录的表项
            var that = this;
            console.log("remove directory");
            axios.post('/cmd', {
                param: ["rmdir", that.tempfilename, ""]
            })
                .then(function (response) {
                    var object = response.data;
                    if (object.code == that.Success.RMDIR){
                        that.getTableData("");
                        setTimeout(function (){
                            that.getDirectory();
                        },100);
                    }else {
                        that.$message(object.msg);
                        console.log(object.msg);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        getTableData: function (path) {
            var that = this;
            axios.post('/cmd', {
                param: ["getTableData", path]
            })
                .then(function (response) {
                    console.log(response.data);
                    that.tableData = response.data;
                })
                .catch(function (error) {
                    console.log(error);
                })
        },
        searchfile: function (queryString, cb) {
            //搜索
            //准备一个数组，用于存放返回的文件项中的各项信息{name:"",path:""}
            //通过遍历整棵目录索引树，查找是否有文件或目录名包含传入的字符串
            //如果有，将其名字和路径添加到该数组，最后返回整个数组
            var that = this;
            if (!queryString) return;
            axios.post('/cmd', {
                param: ["getSearchData", queryString,0]
            })
                .then(function (response) {
                    console.log(response.data);
                    var results = response.data;
                    // 调用 callback 返回建议列表的数据
                    cb(results);
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        addTab(target) {
            console.log(target);
            this.editableTabs.push(target);
            this.editableTabsValue = target.uid;
        },
        removeTab(targetName) {
            let tabs = this.editableTabs;
            let activeName = this.editableTabsValue;
            this.closefile(targetName);
            if (activeName === targetName) {
                tabs.forEach((tab, index) => {
                    if (tab.uid === targetName) {
                        let nextTab = tabs[index + 1] || tabs[index - 1];
                        if (nextTab) {
                            activeName = nextTab.uid;
                        }else{
                            this.tab.tabShow = false;
                        }
                    }
                });
            }
            this.editableTabsValue = activeName;
            this.editableTabs = tabs.filter(tab => tab.uid !== targetName);
        },
        topath:function(){
            var that = this;
            axios.post('/cmd', {
                param: ["cd", that.inputlist.filepath]
            })
                .then(function (response) {
                    console.log(response.data);
                    var object = response.data;
                    if (object.code == that.Success.CD){
                        //将filepath按照/分割，去除空格
                        var arr = that.inputlist.filepath.split("/");
                        that.paths = ['/'];
                        arr.forEach((item,index)=>{
                            if (item!=""){
                                that.paths.push(item);
                            }
                        })
                        that.getTableData("");
                    }else {
                        that.$message(object.msg);
                        console.log(object.msg);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                })
        },
        handleSelect(item){
            var that = this;
            axios.post('/cmd', {
                param: ["cd", item.path]
            })
                .then(function (response) {
                    console.log(response.data);
                    //将filepath按照/分割，去除空格
                    var arr = item.path.split("/");
                    that.paths = ['/'];
                    arr.forEach((item,index)=>{
                        if (item!=""){
                            that.paths.push(item);
                        }
                    })
                    that.getTableData("");
                })
                .catch(function (error) {
                    console.log(error);
                })
        },
        dateFormat:function(row, column) {
            var cjsj = row[column.property];
            if (cjsj == undefined) {
                return "";
            }
            var date = new Date(cjsj) //时间戳为10位需*1000，时间戳为13位的话不需乘1000
            var Y = date.getFullYear() + '-'
            var M = (date.getMonth()+1 < 10 ? '0'+(date.getMonth()+1) : date.getMonth()+1) + '-'
            var D = date.getDate() + ' '
            var h = date.getHours() + ':'
            var m = date.getMinutes() + ':'
            var s = date.getSeconds()
            return Y+M+D+h+m+s;
        },
        showDisk:function(){
            var that = this;
            axios.post('/cmd', {
                param: ["showDisk"]
            })
                .then(function (response) {
                    that.$alert(response.data, '磁盘属性', {
                        dangerouslyUseHTMLString: true
                    });
                })
                .catch(function (error) {
                    console.log(error);
                })
        }
    }
});