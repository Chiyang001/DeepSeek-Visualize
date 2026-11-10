.class public final Llocal/visualize/VisualCompose;
.super Ljava/lang/Object;
.implements Lly4;

.field private final spec:Llocal/visualize/VisualSpec;
.field private final dark:Z
.field private final scope:Llocal/visualize/VisualContext$Scope;

.method public constructor <init>(Llocal/visualize/VisualSpec;ZLlocal/visualize/VisualContext$Scope;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Llocal/visualize/VisualCompose;->spec:Llocal/visualize/VisualSpec;
    iput-boolean p2, p0, Llocal/visualize/VisualCompose;->dark:Z
    iput-object p3, p0, Llocal/visualize/VisualCompose;->scope:Llocal/visualize/VisualContext$Scope;
    return-void
.end method

.method public static render(Llocal/visualize/VisualSpec;Lfk7;Lv15;Lk;)V
    .locals 9
    sget-object v6, Lwya;->f:La73;
    invoke-virtual {p2, v6}, Lv15;->j(Lcu8;)Ljava/lang/Object;
    move-result-object v6
    check-cast v6, Ljava/lang/Boolean;
    if-nez v6, :app_theme
    invoke-static {p2}, Lhc3;->G(Lv15;)Z
    move-result v6
    goto :theme_ready

    :app_theme
    invoke-virtual {v6}, Ljava/lang/Boolean;->booleanValue()Z
    move-result v6

    :theme_ready
    const v7, 0x1d6a84b2
    add-int/2addr v7, v6
    invoke-virtual {p2, v7}, Lv15;->f0(I)V
    invoke-static {p2, p3}, Llocal/visualize/VisualCompose;->scope(Lv15;Lk;)Llocal/visualize/VisualContext$Scope;
    move-result-object v8
    new-instance v0, Llocal/visualize/VisualCompose;
    invoke-direct {v0, p0, v6, v8}, Llocal/visualize/VisualCompose;-><init>(Llocal/visualize/VisualSpec;ZLlocal/visualize/VisualContext$Scope;)V
    const/high16 v1, 0x3f800000    # 1.0f
    invoke-static {p1, v1}, Lh7a;->c(Lfk7;F)Lfk7;
    move-result-object v1
    move-object v2, v0
    move-object v3, p2
    const/4 v4, 0x0
    const/4 v5, 0x0
    invoke-static/range {v0 .. v5}, Lhc3;->c(Lly4;Lfk7;Lly4;Lv15;II)V
    invoke-virtual {p2, v5}, Lv15;->q(Z)V
    return-void
.end method

.method private static scope(Lv15;Lk;)Llocal/visualize/VisualContext$Scope;
    .locals 4
    sget-object v0, Lk07;->o:Lxga;
    invoke-virtual {p0, v0}, Lv15;->j(Lcu8;)Ljava/lang/Object;
    move-result-object v0
    check-cast v0, Lr07;
    iget-object v1, v0, Lr07;->a:Ljava/lang/String;
    iget-object v2, v0, Lr07;->b:Ljava/lang/Integer;
    iget v0, v0, Lr07;->c:I
    iget-object v3, p1, Lk;->a:Ld;
    iget v3, v3, Ld;->b:I
    invoke-static {v1, v2, v0, v3}, Llocal/visualize/VisualContext;->scope(Ljava/lang/String;Ljava/lang/Integer;II)Llocal/visualize/VisualContext$Scope;
    move-result-object v0
    return-object v0
.end method

.method public final j(Ljava/lang/Object;)Ljava/lang/Object;
    .locals 2
    instance-of v0, p1, Landroid/content/Context;
    if-eqz v0, :update
    check-cast p1, Landroid/content/Context;
    new-instance v0, Llocal/visualize/VisualCard;
    iget-boolean v1, p0, Llocal/visualize/VisualCompose;->dark:Z
    invoke-direct {v0, p1, v1}, Llocal/visualize/VisualCard;-><init>(Landroid/content/Context;Z)V
    return-object v0

    :update
    check-cast p1, Llocal/visualize/VisualCard;
    iget-object v0, p0, Llocal/visualize/VisualCompose;->spec:Llocal/visualize/VisualSpec;
    iget-object v1, p0, Llocal/visualize/VisualCompose;->scope:Llocal/visualize/VisualContext$Scope;
    invoke-virtual {p1, v0, v1}, Llocal/visualize/VisualCard;->bind(Llocal/visualize/VisualSpec;Llocal/visualize/VisualContext$Scope;)V
    sget-object v0, Lghb;->a:Lghb;
    return-object v0
.end method
