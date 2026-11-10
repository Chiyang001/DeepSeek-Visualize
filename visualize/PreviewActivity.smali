.class public final Llocal/visualize/PreviewActivity;
.super Llt;
.implements Llocal/visualize/PreviewPage$Content;

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Llt;-><init>()V
    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .locals 0
    invoke-super {p0, p1}, Llt;->onCreate(Landroid/os/Bundle;)V
    invoke-static {p0, p0}, Llocal/visualize/PreviewPage;->show(Landroid/app/Activity;Llocal/visualize/PreviewPage$Content;)V
    return-void
.end method

.method public markdown(Ljava/lang/String;)Landroid/view/View;
    .locals 1
    invoke-static {p0, p1}, Llocal/visualize/PreviewMarkdown;->create(Llt;Ljava/lang/String;)Landroid/view/View;
    move-result-object v0
    return-object v0
.end method
