### 1、权限模式scheme

`scheme`，有几种常用权限模式，如`world`、`auth`、`digest`、`ip`。

（1）`World`是一种最开放的权限控制模式，这种权限控制方式几乎没有任何作用，数据节点的访问权限对所有用户开放。`World`模式也可以看作是一种特殊的`Digest`模式，它只有一个权限标识，即`worl:anyone`。

（2）`Digest`是最常用的权限控制模式，也更符合我们对于权限控制的认识，其以类似于`username:password`形式的权限标识进行权限配置，便于区分不同应用来进行权限控制。