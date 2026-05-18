# ==============================================
# Cherry Office 库 ProGuard 混淆规则
# ==============================================

# ============ 基础保留规则 ============

# 保留对外暴露的 public 类、接口
-keep public class com.cherry.lib.doc.** { *; }
-keep public class com.cherry.lib.pdf.** { *; }

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# 保留 Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# 保留 R 文件（资源引用）
-keep class **.R { *; }
-keep class **.R$* { *; }
-keepclassmembers class **.R$* {
    public static <fields>;
}

# 保留 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留 View 相关
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
    public void set*(...);
}

# 保留自定义 View 的构造方法
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# 保留 Drawable
-keep class android.graphics.drawable.** { *; }

# 内部类引用外部类
-keepclasseswithmembernames class * {
    public <init>(...);
}

# 反射调用
-keepattributes InnerClasses
-keepattributes EnclosingMethod
