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
        row: {
            filename: "",
            modtime: "",
            type: "",
            size: 0
        },
        inputlist: {
            input_show: false,
            filepath: "",
            filename: ""
        },
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
        textedit: {
            uid: 0,
            card_show: false,
            filecontent: ""
        },
        copy: {
            bufferid: 0
        },
        Success:{
            WRITE:-7,
            DELETE:-8,
            CREATE:-9,
            READ:-10,
            RMDIR:-11,
            MKDIR:-12,
            PASTE:-13,
            RENAME:-14
        }
    },
    created: function () {
        var that = this;
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
        //从后端获取目录数据
        var that = this;
        axios.post('/cmd', {
            param: ["getDirectory"]
        })
            .then(function (response) {
                console.log(response.data);
                for (let i = 0; i < response.data.children.length; i++) {
                    that.data.push(response.data.children[i]);
                }
                that.getTableData("/");
            })
            .catch(function (error) {
                console.log(error);
            })
        //根据目录查询对应的数据
    },
    methods: {
        handleNodeClick(data) {
            console.log(data);
        },
        handleCurrentChange(val) {
            this.currentRow = val;
        },
        handleBreadClick() {
            this.inputlist.input_show = true;
            var str = this.paths.join("/");
            this.inputlist.filepath = str;
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
                        that.tableData = response.data;
                    })
                    .catch(function (error) {
                        console.log(error);
                    })
            }
        },
        handleContentChange() {
            console.log(this.textedit.filecontent)
        },
        writefile() {
            var that = this;
            axios.post('/cmd', {
                param: ["write", this.textedit.uid, this.textedit.filecontent]
            })
                .then(function (response) {
                    var object = response.data;
                    if (object.code == that.Success.WRITE)
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
        closefile() {
            var that = this;
            axios.post('/cmd', {
                param: ["close", this.textedit.uid]
            })
                .then(function (response) {
                    console.log(response.data);
                    that.textedit.card_show = false;
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
            axios.post('/cmd', {
                param: ["cd", "/" + this.paths.join("/")]
            })
                .then(function (response) {
                    console.log(response.data);
                    that.tableData = response.data;
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
            this.row = row;
        },
        openfile: function () {
            console.log("open file");
            var that = this;
            axios.post('/cmd', {
                param: ["open", that.row.filename, "", 3]
            })
                .then(function (response) {
                    console.log(response);
                    var object = response.data;
                    if (object.code < 0) {
                        console.log(object.msg);
                        that.$message(object.msg);
                    } else {
                        that.textedit.uid = object.code;
                        axios.post('/cmd', {
                            param: ["read", object.code]
                        })
                            .then(function (response) {
                                console.log(response);
                                var object2 = response.data;
                                if (object2.code == 0) {
                                    that.textedit.filecontent = object2.content;
                                    that.textedit.card_show = true;
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
                param: ["delete", that.row.filename, ""]
            })
                .then(function (response) {
                    console.log(response);
                    that.getTableData("");
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
                param: ["copy", "a.txt", ""]
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
                param: ["paste", "a.txt", "", that.copy.bufferid]
            })
                .then(function (response) {
                    console.log(response);
                    that.getTableData("");
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
                param: ["rename", that.row.filename, "", "b.txt"]
            })
                .then(function (response) {
                    var obj = response.data;
                    if (obj.code != -14) {
                        console.log(obj.msg);
                        that.$message(obj.msg);
                    } else {
                        that.getTableData("");
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        showfile: function () {
            console.log("show fileProperty");
            //根据文件名和文件路径查找到对应的目录项和文件fcb，读取文件名和其它所有的属性的值，以json的格式返回文件属性信息
            axios.post('/cmd', {
                param: ["showProperty", "a.txt", "/li554/test1"]
            })
                .then(function (response) {
                    console.log(response);
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        createfile: function () {
            console.log("create file");
            var that = this;
            axios.post('/cmd', {
                param: ["create", "a.txt", ""]
            })
                .then(function (response) {
                    var object = response.data;
                    if (object.code==that.Success.CREATE)
                        that.getTableData("");
                    else{
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
                param: ["mkdir", "newdir", ""]
            })
                .then(function (response) {
                    var object =  response.data;
                    if (object.code==that.Success.MKDIR)
                        that.getTableData("");
                    else {
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
                param: ["rmdir", that.row.filename, ""]
            })
                .then(function (response) {
                    var object =  response.data;
                    if (object.code==that.Success.RMDIR)
                        that.getTableData("");
                    else {
                        that.$message(object.msg);
                        console.log(object.msg);
                    }
                })
                .catch(function (error) {
                    console.log(error);
                });
        },
        getTableData:function (path){
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
        searchfile: function () {
            //搜索
            //准备一个数组，用于存放返回的文件项中的各项信息{name:"",path:""}
            //通过遍历整棵目录索引树，查找是否有文件或目录名包含传入的字符串
            //如果有，将其名字和路径添加到该数组，最后返回整个数组
            var that = this;
            axios.post('/cmd', {
                param: ["search", that.inputlist.filename]
            })
                .then(function (response) {
                    console.log(response);
                })
                .catch(function (error) {
                    console.log(error);
                });
        }
    }
});