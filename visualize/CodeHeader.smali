.class public final Llocal/visualize/CodeHeader;
.super Ljava/lang/Object;
.implements Ljy4;

.field private context:Landroid/content/Context;
.field private language:Ljava/lang/String;
.field private source:Ljava/lang/String;
.field private preview:Z
.field private dark:Z

.method public constructor <init>()V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    return-void
.end method

# Add actions to the original header, preserving its copy action, typography and theme.
.method public static augment(Le07;Ljy4;ZLv15;Ljava/util/List;)Ljava/util/List;
    .locals 10
    instance-of v0, p0, Luz6;
    if-eqz v0, :diagram
    check-cast p0, Luz6;
    iget-object v1, p0, Luz6;->a:Ljava/lang/String;
    goto :language_ready
    :diagram
    instance-of v0, p0, Ld07;
    if-eqz v0, :unchanged
    const-string v1, "mermaid"
    :language_ready
    invoke-interface {p1}, Ljy4;->w()Ljava/lang/Object;
    move-result-object v2
    check-cast v2, Ljava/lang/String;
    sget-object v0, Lfe;->b:Lxga;
    invoke-virtual {p3, v0}, Lv15;->j(Lcu8;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Landroid/content/Context;
    sget-object v3, Lwya;->f:La73;
    invoke-virtual {p3, v3}, Lv15;->j(Lcu8;)Ljava/lang/Object;
    move-result-object v3
    check-cast v3, Ljava/lang/Boolean;
    if-eqz v3, :system_theme
    invoke-virtual {v3}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v3
    goto :theme_ready
    :system_theme
    invoke-static {p3}, Lhc3;->G(Lv15;)Z
    move-result v3
    :theme_ready
    new-instance v4, Ljava/util/ArrayList;
    invoke-direct {v4}, Ljava/util/ArrayList;-><init>()V
    invoke-static {v1}, Llocal/visualize/CodeActions;->isHtml(Ljava/lang/String;)Z
    move-result v6
    if-eqz v6, :download
    new-instance v5, Llocal/visualize/CodeHeader;
    invoke-direct {v5}, Llocal/visualize/CodeHeader;-><init>()V
    iput-object v0, v5, Llocal/visualize/CodeHeader;->context:Landroid/content/Context;
    iput-object v1, v5, Llocal/visualize/CodeHeader;->language:Ljava/lang/String;
    iput-object v2, v5, Llocal/visualize/CodeHeader;->source:Ljava/lang/String;
    iput-boolean v3, v5, Llocal/visualize/CodeHeader;->dark:Z
    iput-boolean v6, v5, Llocal/visualize/CodeHeader;->preview:Z
    invoke-static {v5, p2, p3}, Llocal/visualize/CodeHeader;->action(Llocal/visualize/CodeHeader;ZLv15;)Loz6;
    move-result-object v5
    invoke-virtual {v4, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    :download
    new-instance v5, Llocal/visualize/CodeHeader;
    invoke-direct {v5}, Llocal/visualize/CodeHeader;-><init>()V
    iput-object v0, v5, Llocal/visualize/CodeHeader;->context:Landroid/content/Context;
    iput-object v1, v5, Llocal/visualize/CodeHeader;->language:Ljava/lang/String;
    iput-object v2, v5, Llocal/visualize/CodeHeader;->source:Ljava/lang/String;
    iput-boolean v3, v5, Llocal/visualize/CodeHeader;->dark:Z
    invoke-static {v5, p2, p3}, Llocal/visualize/CodeHeader;->action(Llocal/visualize/CodeHeader;ZLv15;)Loz6;
    move-result-object v5
    invoke-virtual {v4, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
    invoke-virtual {v4, p4}, Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z
    return-object v4
    :unchanged
    return-object p4
.end method

.method public static standalone(Ljava/lang/String;Ljava/lang/String;ZLv15;)V
    .locals 8
    new-instance v0, Luz6;
    invoke-direct {v0, p0}, Luz6;-><init>(Ljava/lang/String;)V
    new-instance v1, Lvd0;
    const/4 v2, 0x1
    invoke-direct {v1, p1, v2}, Lvd0;-><init>(Ljava/lang/String;I)V
    move v2, p2
    const/4 v3, 0x0
    const-wide/16 v4, 0x0
    move-object v6, p3
    const/4 v7, 0x0
    invoke-static/range {v0 .. v7}, Lsz6;->a(Le07;Ljy4;ZLfk7;JLv15;I)V
    return-void
.end method

.method private static action(Llocal/visualize/CodeHeader;ZLv15;)Loz6;
    .locals 5
    iget-boolean v0, p0, Llocal/visualize/CodeHeader;->preview:Z
    const v1, 0x43b18830
    add-int/2addr v1, v0
    new-instance v4, Llocal/visualize/CodeIconContent;
    invoke-direct {v4, p0}, Llocal/visualize/CodeIconContent;-><init>(Llocal/visualize/CodeHeader;)V
    invoke-static {v1, v4, p2}, Le8c;->y0(ILvy4;Lv15;)Lw33;
    move-result-object v1
    const-string v2, "下载"
    if-eqz v0, :label_ready
    const-string v2, "预览"
    :label_ready
    new-instance v3, Loz6;
    invoke-direct {v3, v2, v1, p0, p1}, Loz6;-><init>(Ljava/lang/String;Lw33;Ljy4;Z)V
    return-object v3
.end method

.method public final w()Ljava/lang/Object;
    .locals 4
    iget-object v0, p0, Llocal/visualize/CodeHeader;->context:Landroid/content/Context;
    iget-object v1, p0, Llocal/visualize/CodeHeader;->language:Ljava/lang/String;
    iget-object v2, p0, Llocal/visualize/CodeHeader;->source:Ljava/lang/String;
    iget-boolean v3, p0, Llocal/visualize/CodeHeader;->preview:Z
    invoke-static {v0, v1, v2, v3}, Llocal/visualize/CodeActions;->open(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Z)V
    sget-object v0, Lghb;->a:Lghb;
    return-object v0
.end method

.method public final q(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    .locals 6
    check-cast p1, Lv15;
    const v0, 0x43b18832
    invoke-virtual {p1, v0}, Lv15;->f0(I)V
    new-instance v0, Llocal/visualize/CodeIconFactory;
    invoke-direct {v0, p0}, Llocal/visualize/CodeIconFactory;-><init>(Llocal/visualize/CodeHeader;)V
    sget-object v1, Lck7;->a:Lck7;
    move-object v2, v0
    move-object v3, p1
    const/4 v4, 0x0
    const/4 v5, 0x0
    invoke-static/range {v0 .. v5}, Lhc3;->c(Lly4;Lfk7;Lly4;Lv15;II)V
    invoke-virtual {p1, v5}, Lv15;->q(Z)V
    sget-object v0, Lghb;->a:Lghb;
    return-object v0
.end method

.method public final j(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 3
    instance-of v0, p1, Landroid/content/Context;
    if-eqz v0, :update
    check-cast p1, Landroid/content/Context;
    new-instance v0, Llocal/visualize/CodeIcon;
    invoke-direct {v0, p1}, Llocal/visualize/CodeIcon;-><init>(Landroid/content/Context;)V
    return-object v0
    :update
    check-cast p1, Llocal/visualize/CodeIcon;
    iget-boolean v0, p0, Llocal/visualize/CodeHeader;->preview:Z
    iget-boolean v1, p0, Llocal/visualize/CodeHeader;->dark:Z
    invoke-virtual {p1, v0, v1}, Llocal/visualize/CodeIcon;->bind(ZZ)V
    sget-object v0, Lghb;->a:Lghb;
    return-object v0
.end method
