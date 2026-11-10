.class public final Llocal/visualize/PreviewMarkdown;
.super Ljava/lang/Object;
.implements Lzy4;

.field private final source:Ljava/lang/String;
.field private final node:Lk;
.field private final body:Z

.method private constructor <init>(Ljava/lang/String;Lk;Z)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Llocal/visualize/PreviewMarkdown;->source:Ljava/lang/String;
    iput-object p2, p0, Llocal/visualize/PreviewMarkdown;->node:Lk;
    iput-boolean p3, p0, Llocal/visualize/PreviewMarkdown;->body:Z
    return-void
.end method

.method public static create(Llt;Ljava/lang/String;)Landroid/view/View;
    .locals 8
    new-instance v5, Le27;
    new-instance v6, Lvf;
    const/4 v1, 0x0
    const/4 v7, 0x2
    invoke-direct {v6, v1, v7}, Lvf;-><init>(ZI)V
    invoke-direct {v5, v6}, Le27;-><init>(Lvf;)V
    move-object v0, p1
    move v2, v1
    const v3, 0x7fffffff
    move v4, v1
    invoke-static/range {v0 .. v5}, Lf17;->h(Ljava/lang/String;ZZIZLe27;)Lq2b;
    move-result-object v0
    invoke-virtual {v0}, Lq2b;->b()Ljava/util/List;
    move-result-object v0
    invoke-interface {v0, v1}, Ljava/util/List;->get(I)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lk;
    new-instance v2, Llocal/visualize/PreviewMarkdown;
    invoke-direct {v2, p1, v0, v1}, Llocal/visualize/PreviewMarkdown;-><init>(Ljava/lang/String;Lk;Z)V
    new-instance v3, Landroidx/compose/ui/platform/ComposeView;
    invoke-direct {v3, p0}, Landroidx/compose/ui/platform/ComposeView;-><init>(Llt;)V
    invoke-virtual {v3, v2}, Landroidx/compose/ui/platform/ComposeView;->setContent(Lzy4;)V
    return-object v3
.end method

.method public final q(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    .locals 9
    check-cast p1, Lv15;
    iget-object v1, p0, Llocal/visualize/PreviewMarkdown;->source:Ljava/lang/String;
    iget-object v2, p0, Llocal/visualize/PreviewMarkdown;->node:Lk;
    iget-boolean v0, p0, Llocal/visualize/PreviewMarkdown;->body:Z
    if-eqz v0, :provide
    move-object v0, v1
    move-object v1, v2
    const/4 v2, 0x1    # The preview already provides its own outer padding.
    sget-object v3, Lck7;->a:Lck7;
    move-object v4, p1
    const/4 v5, 0x0
    invoke-static/range {v0 .. v5}, Lg07;->c(Ljava/lang/String;Lk;ZLfk7;Lv15;I)V
    goto :done

    :provide
    new-instance v8, Llocal/visualize/PreviewMarkdown;
    const/4 v3, 0x1
    invoke-direct {v8, v1, v2, v3}, Llocal/visualize/PreviewMarkdown;-><init>(Ljava/lang/String;Lk;Z)V
    new-instance v0, Ll07;
    const/4 v1, 0x0
    const/4 v2, 0x1
    const/4 v3, 0x0
    const/4 v4, 0x0
    const/4 v5, 0x0
    const/4 v6, 0x0
    const/16 v7, 0x58d
    invoke-direct/range {v0 .. v7}, Ll07;-><init>(Lu4;ZLjy4;ZIZI)V
    sget-object v1, Lk07;->n:Lxga;
    invoke-virtual {v1, v0}, Lxga;->a(Ljava/lang/Object;)Leu8;
    move-result-object v0
    filled-new-array {v0}, [Leu8;
    move-result-object v0
    const/16 v1, 0x8
    invoke-static {v0, v8, p1, v1}, Ltdb;->n([Leu8;Lzy4;Lv15;I)V

    :done
    sget-object v0, Lghb;->a:Lghb;
    return-object v0
.end method
