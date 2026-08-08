# Keep kotlinx.serialization classes (they're accessed via reflection/generated code)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.matejgroombridge.milestones.**$$serializer { *; }
-keepclassmembers class dev.matejgroombridge.milestones.** {
    *** Companion;
}
-keepclasseswithmembers class dev.matejgroombridge.milestones.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# MilestoneUnit is a sealed hierarchy serialized polymorphically, so R8 must
# keep the generated subclass serializers and the sealed-class metadata that
# kotlinx uses to resolve the "type" discriminator at runtime.
-keep class dev.matejgroombridge.milestones.data.model.MilestoneUnit { *; }
-keep class dev.matejgroombridge.milestones.data.model.MilestoneUnit$* { *; }
