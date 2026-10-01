# BookingHotel release rules.
# Keep Retrofit/Gson DTO field names because Gson serializes/deserializes them reflectively.
-keep class com.example.bookinghotel.data.remote.dto.** { *; }

# Retrofit uses generic signatures and runtime annotations to resolve service methods.
-keepattributes Signature
-keepattributes *Annotation*

# Preserve useful source/line information for production stack traces and R8 mapping.
-keepattributes SourceFile,LineNumberTable
