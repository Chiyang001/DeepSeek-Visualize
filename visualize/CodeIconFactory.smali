.class public final Llocal/visualize/CodeIconFactory;
.super Ljava/lang/Object;
.implements Lly4;

.field private final action:Llocal/visualize/CodeHeader;

.method public constructor <init>(Llocal/visualize/CodeHeader;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Llocal/visualize/CodeIconFactory;->action:Llocal/visualize/CodeHeader;
    return-void
.end method

.method public final j(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 1
    iget-object v0, p0, Llocal/visualize/CodeIconFactory;->action:Llocal/visualize/CodeHeader;
    invoke-virtual {v0, p1}, Llocal/visualize/CodeHeader;->j(Ljava/lang/Object;)Ljava/lang/Object;
    move-result-object v0
    return-object v0
.end method
