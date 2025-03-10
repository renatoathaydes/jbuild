����   =�
      java/lang/Object <init> ()V    	 
 apply N(Ljava/util/function/Consumer;Ljava/lang/Object;)Ljava/util/function/Function;      java/util/Map isEmpty ()Z     of ()Ljava/util/Map;
      &java/util/concurrent/CompletableFuture completedStage :(Ljava/lang/Object;)Ljava/util/concurrent/CompletionStage;  &java/util/concurrent/ConcurrentHashMap      size ()I
  "  # (I)V
    & ' ( accept �(Ljava/util/concurrent/ConcurrentHashMap;Ljava/util/Map;Ljava/util/concurrent/CompletableFuture;)Ljava/util/function/BiConsumer;  * + , forEach "(Ljava/util/function/BiConsumer;)V .  / java/util/List . 1  2 ()Ljava/util/List; 4 *java/util/concurrent/ConcurrentLinkedDeque
 3   7 ' 8 �(Ljava/util/concurrent/ConcurrentLinkedDeque;Ljava/util/List;Ljava/util/concurrent/CompletableFuture;)Ljava/util/function/Consumer; . : + ;  (Ljava/util/function/Consumer;)V = )java/util/concurrent/atomic/AtomicBoolean
 < ?  @ (Z)V  B ' C �(Ljava/util/concurrent/atomic/AtomicBoolean;Ljava/util/concurrent/CompletableFuture;Ljava/util/concurrent/ConcurrentLinkedDeque;Ljava/util/List;)Ljava/util/function/Consumer; E )java/util/concurrent/atomic/AtomicInteger
 D "  H I J entrySet ()Ljava/util/Set; L M N O P java/util/Set iterator ()Ljava/util/Iterator; R S T U  java/util/Iterator hasNext R W X Y next ()Ljava/lang/Object; [ java/util/Map$Entry Z ] ^ Y getValue ` $java/util/concurrent/CompletionStage  b ' c �(Ljava/util/concurrent/CompletableFuture;Ljava/util/Map$Entry;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/Function;Ljava/util/Map;)Ljava/util/function/BiConsumer; _ e f g whenComplete G(Ljava/util/function/BiConsumer;)Ljava/util/concurrent/CompletionStage; _ i j k handle G(Ljava/util/function/BiFunction;)Ljava/util/concurrent/CompletionStage;  m 	 n ()Ljava/util/function/Function; _ p q r thenCompose E(Ljava/util/function/Function;)Ljava/util/concurrent/CompletionStage; t u v w Y java/util/function/Supplier get y java/lang/Throwable { | } 	 ~ java/util/function/BiFunction 8(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;  � 	 � r(ILjava/time/Duration;Ljava/util/function/Supplier;Ljava/util/function/BiFunction;)Ljava/util/function/BiFunction;
 � � � � � jbuild/util/AsyncUtils handlingAsync m(Ljava/util/concurrent/CompletionStage;Ljava/util/function/BiFunction;)Ljava/util/concurrent/CompletionStage;  � � � run [(Ljava/util/concurrent/CompletableFuture;Ljava/util/function/Supplier;)Ljava/lang/Runnable; � � � � � $java/util/concurrent/ExecutorService submit 3(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;	 � � � � 	scheduler /Ljava/util/concurrent/ScheduledExecutorService;  � � � R(Ljava/lang/Runnable;Ljava/util/concurrent/CompletableFuture;)Ljava/lang/Runnable; � � � -java/util/concurrent/ScheduledExecutorService 	 � � � [(Ljava/util/function/Supplier;Ljava/util/concurrent/CompletableFuture;)Ljava/lang/Runnable;
 � � � � � java/time/Duration toMillis ()J	 � � � � � java/util/concurrent/TimeUnit MILLISECONDS Ljava/util/concurrent/TimeUnit; � � � � schedule \(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture; 
 � ' � I(Ljava/util/concurrent/CompletableFuture;)Ljava/util/function/BiConsumer;
  � � � completeExceptionally (Ljava/lang/Throwable;)Z
  � � � complete (Ljava/lang/Object;)Z � � � �  java/lang/Runnable � java/lang/Thread � jbuild-scheduler
 � �  � )(Ljava/lang/Runnable;Ljava/lang/String;)V
 � � � @ 	setDaemon  � w � p(Ljava/util/function/Supplier;ILjava/time/Duration;Ljava/util/function/BiFunction;)Ljava/util/function/Supplier;
 � � � � 
afterDelay Y(Ljava/time/Duration;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletionStage;
 � � � � withRetries y(Ljava/util/function/Supplier;ILjava/time/Duration;Ljava/util/function/BiFunction;)Ljava/util/concurrent/CompletionStage;
  � �  isDone Z � � � setValue &(Ljava/lang/Object;)Ljava/lang/Object;
 D � �   decrementAndGet � � � 	 � java/util/function/Function  � ' � �(Ljava/util/concurrent/atomic/AtomicBoolean;Ljava/util/concurrent/CompletableFuture;Ljava/util/concurrent/ConcurrentLinkedDeque;Ljava/util/List;)Ljava/util/function/BiConsumer;
 < � � � compareAndSet (ZZ)Z
 3 � � � add
 3  .   � ' � �(Ljava/util/concurrent/ConcurrentLinkedDeque;Ljava/util/List;Ljava/util/concurrent/CompletableFuture;)Ljava/util/function/BiConsumer;
 � � � � � jbuild/util/Either left ((Ljava/lang/Object;)Ljbuild/util/Either;
 � � � � right  � ' � �(Ljava/util/concurrent/ConcurrentHashMap;Ljava/lang/Object;Ljava/util/Map;Ljava/util/concurrent/CompletableFuture;)Ljava/util/function/BiConsumer;
  � � ~ put
   ' java/util/function/Consumer (Ljava/lang/Object;)V  	newThread &()Ljava/util/concurrent/ThreadFactory;

 java/util/concurrent/Executors  newSingleThreadScheduledExecutor U(Ljava/util/concurrent/ThreadFactory;)Ljava/util/concurrent/ScheduledExecutorService; Code LineNumberTable 	returning N(Ljava/lang/Object;Ljava/util/function/Consumer;)Ljava/util/function/Function; MethodParameters value consumer 	Signature v<T:Ljava/lang/Object;V:Ljava/lang/Object;>(TV;Ljava/util/function/Consumer<TT;>;)Ljava/util/function/Function<TT;TV;>; awaitValues 7(Ljava/util/Map;)Ljava/util/concurrent/CompletionStage; StackMapTable map �<K:Ljava/lang/Object;V:Ljava/lang/Object;>(Ljava/util/Map<TK;+Ljava/util/concurrent/CompletionStage<TV;>;>;)Ljava/util/concurrent/CompletionStage<Ljava/util/Map<TK;Ljbuild/util/Either<TV;Ljava/lang/Throwable;>;>;>; 8(Ljava/util/List;)Ljava/util/concurrent/CompletionStage; list �<T:Ljava/lang/Object;>(Ljava/util/List<+Ljava/util/concurrent/CompletionStage<TT;>;>;)Ljava/util/concurrent/CompletionStage<Ljava/util/Collection<Ljbuild/util/Either<TT;Ljava/lang/Throwable;>;>;>; awaitSuccessValues �<T:Ljava/lang/Object;>(Ljava/util/List<+Ljava/util/concurrent/CompletionStage<TT;>;>;)Ljava/util/concurrent/CompletionStage<Ljava/util/Collection<TT;>;>; T(Ljava/util/Map;Ljava/util/function/Function;)Ljava/util/concurrent/CompletionStage; mapper �<K:Ljava/lang/Object;T:Ljava/lang/Object;V:Ljava/lang/Object;>(Ljava/util/Map<TK;Ljava/util/concurrent/CompletionStage<TT;>;>;Ljava/util/function/Function<Ljava/util/Map<TK;TT;>;TV;>;)Ljava/util/concurrent/CompletionStage<TV;>; future handler �<T:Ljava/lang/Object;U:Ljava/lang/Object;>(Ljava/util/concurrent/CompletionStage<TT;>;Ljava/util/function/BiFunction<TT;Ljava/lang/Throwable;Ljava/util/concurrent/CompletionStage<TU;>;>;)Ljava/util/concurrent/CompletionStage<TU;>; retries delayOnRetry<T:Ljava/lang/Object;U:Ljava/lang/Object;>(Ljava/util/function/Supplier<Ljava/util/concurrent/CompletionStage<TT;>;>;ILjava/time/Duration;Ljava/util/function/BiFunction<TT;Ljava/lang/Throwable;Ljava/util/concurrent/CompletionStage<TU;>;>;)Ljava/util/concurrent/CompletionStage<TU;>; getAsync k(Ljava/util/function/Supplier;Ljava/util/concurrent/ExecutorService;)Ljava/util/concurrent/CompletionStage; supplier service �<T:Ljava/lang/Object;>(Ljava/util/function/Supplier<TT;>;Ljava/util/concurrent/ExecutorService;)Ljava/util/concurrent/CompletionStage<TT;>; runAsync <(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletionStage; runnable N(Ljava/lang/Runnable;)Ljava/util/concurrent/CompletionStage<Ljava/lang/Void;>; delay futureGetter �<T:Ljava/lang/Object;>(Ljava/time/Duration;Ljava/util/function/Supplier<Ljava/util/concurrent/CompletionStage<TT;>;>;)Ljava/util/concurrent/CompletionStage<TT;>; lambda$afterDelay$15 H(Ljava/util/function/Supplier;Ljava/util/concurrent/CompletableFuture;)V lambda$afterDelay$14 R(Ljava/util/concurrent/CompletableFuture;Ljava/lang/Object;Ljava/lang/Throwable;)V lambda$runAsync$13 ?(Ljava/lang/Runnable;Ljava/util/concurrent/CompletableFuture;)V lambda$getAsync$12 H(Ljava/util/concurrent/CompletableFuture;Ljava/util/function/Supplier;)V lambda$static$11 ((Ljava/lang/Runnable;)Ljava/lang/Thread; lambda$withRetries$10 �(ILjava/time/Duration;Ljava/util/function/Supplier;Ljava/util/function/BiFunction;Ljava/lang/Object;Ljava/lang/Throwable;)Ljava/util/concurrent/CompletionStage; lambda$withRetries$9 lambda$handlingAsync$8 N(Ljava/util/concurrent/CompletionStage;)Ljava/util/concurrent/CompletionStage; lambda$awaitValues$7 �(Ljava/util/concurrent/CompletableFuture;Ljava/util/Map$Entry;Ljava/util/concurrent/atomic/AtomicInteger;Ljava/util/function/Function;Ljava/util/Map;Ljava/lang/Object;Ljava/lang/Throwable;)V lambda$awaitSuccessValues$6 �(Ljava/util/concurrent/atomic/AtomicBoolean;Ljava/util/concurrent/CompletableFuture;Ljava/util/concurrent/ConcurrentLinkedDeque;Ljava/util/List;Ljava/util/concurrent/CompletionStage;)V lambda$awaitSuccessValues$5 �(Ljava/util/concurrent/atomic/AtomicBoolean;Ljava/util/concurrent/CompletableFuture;Ljava/util/concurrent/ConcurrentLinkedDeque;Ljava/util/List;Ljava/lang/Object;Ljava/lang/Throwable;)V lambda$awaitValues$4 �(Ljava/util/concurrent/ConcurrentLinkedDeque;Ljava/util/List;Ljava/util/concurrent/CompletableFuture;Ljava/util/concurrent/CompletionStage;)V lambda$awaitValues$3 �(Ljava/util/concurrent/ConcurrentLinkedDeque;Ljava/util/List;Ljava/util/concurrent/CompletableFuture;Ljava/lang/Object;Ljava/lang/Throwable;)V lambda$awaitValues$2 �(Ljava/util/concurrent/ConcurrentHashMap;Ljava/util/Map;Ljava/util/concurrent/CompletableFuture;Ljava/lang/Object;Ljava/util/concurrent/CompletionStage;)V lambda$awaitValues$1 �(Ljava/util/concurrent/ConcurrentHashMap;Ljava/lang/Object;Ljava/util/Map;Ljava/util/concurrent/CompletableFuture;Ljava/lang/Object;Ljava/lang/Throwable;)V lambda$returning$0 U(Ljava/util/function/Consumer;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; <clinit> 
SourceFile AsyncUtils.java BootstrapMethods[
\]^_` "java/lang/invoke/LambdaMetafactory metafactory �(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; �c
 �dTUf '(Ljava/lang/Object;Ljava/lang/Object;)Vh
 �iPQk ;(Ljava/lang/Object;Ljava/util/concurrent/CompletionStage;)Vn
 �oLMq )(Ljava/util/concurrent/CompletionStage;)Vs
 �tHIv
 �wFGy *(Ljava/lang/Object;Ljava/lang/Throwable;)V{
 �|DEE ~�
 ��AB� O(Ljava/lang/Object;Ljava/lang/Throwable;)Ljava/util/concurrent/CompletionStage; �
 ��=>�
 ��;<�
 ��78�
 ��9: Y�
 ��C �� (()Ljava/util/concurrent/CompletionStage;�
 ��JK�
 ��NO�
 ��RS@�
 ��?@ InnerClasses Entry� %java/lang/invoke/MethodHandles$Lookup� java/lang/invoke/MethodHandles Lookup 1 �      � �               *� �           	          +*�   �             	        	    k     6*�  � 
� � �� Y*�  � !L� Y� $M*+*,� %  � ) ,�          # 	 $  '  ( & * 4 3             	    e     0*� - � 
� 0� �� 3Y� 5L� Y� $M*+*,� 6  � 9 ,�          8 	 9  <  =   ? . F             	     s     :*� - � 
� 0� �� <Y� >L� 3Y� 5M� Y� $N*+-,*� A  � 9 -�          K 	 L  O  P ! Q ) S 8 `            ! 	"    �     ]*M� Y� $N� DY*�  � F:*� G � K :� Q � .� V � Z:� \ � _-+,� a  � d W���-�          g  h 
 i  k < l X v [ x    � &   �   D R  � 4   	  #     $ 	 � �    *     *+� h � l  � o �          ~   	%  &     ' 	 � �    n     ,*� s � _:� :-� z � _�,*-�   � ��      x        �  �  �  �  �    N x�  _   %  (  )   j     * 	+,    8     � Y� $M+,*� �  � � W,�          �  �  �   	-  .     / 	01    :     � Y� $L� �*+� �  � � W+�          �  �  �   2     3 	 � �    I     !� Y� $M� �+,� �  *� �� �� � W,�          �  �  �  �  �   	4  5     6
78    ]      *� s � _+� �  � d W� 
M+,� �W�      x        �  �  �  �  �    X x
9:    >     ,� *+� �W� 	*,� �W�          �  �  �    
;<    X     *� � +� �W� 
M+,� �W�      x        �  �  �  �  �  �    O x
=>    S     *+� s � �W� 
M*,� �W�      x        �  �  �  �  �    N x
?@    2     � �Y*�� �L+� �+�          �  �  �
AB    n     4� %� +,+-� �  � ˧ -� z � _�-� z � _�          �  � 	 �  � & � ' �    L _ 
C �    "     
*d,-� ϰ          �
DE         *�          ~
FG      	   :*� ә �� 
*� �W+:� � W,� �6� *-� � � �W�      "    m  n  o  p ! q ' r , s 9 u    � $ Z
HI    *     *+,-� �  � d W�          S
JK    m     3� *� � '+� �W� ,� �W,� �-� � � 	+,� �W�          T  U  V  Y  Z , [ 2 ^    
LM    (     -*+,� �  � d W�          ?
NO    w     **� 
-� � � �� �W*� �+� � � 	,*� �W�          @  A # B ) D   # M 3�   3 .   x  3 �
PQ    .     *-+,� �  � d W�      
    +  1
RS    �     ,*+� � �� � � �W*� �,�  � 	-*� �W�          ,  - % . + 0   D �        x   �        x    �
TU    %     	*,�  +�      
       V     $      �  �	� ��          � W   XY   � Z abaZ egjZ lmpZ lrpZ euxZ az}Z ~�Z ���Z ���Z ���Z e�xZ ���Z e�xZ e�xZ e�xZ ����     Z �	��� 