.class public final Llocal/visualize/VisualRequest;
.super Ljava/lang/Object;

.method public static expand(Ljava/lang/String;Lqy1;)Ljava/lang/String;
    .locals 3
    iget-object v0, p1, Lqy1;->a:Ljava/lang/String;
    iget-object v1, p1, Lqy1;->b:Ljava/lang/Integer;
    iget-object v2, p1, Lqy1;->j:Ljava/lang/String;
    invoke-static {p0, v0, v1, v2, p1}, Llocal/visualize/VisualPrompt;->expandForRequest(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
    move-result-object v0
    return-object v0
.end method
