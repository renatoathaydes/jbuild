����   = @
      java/lang/Object <init> ()V	  	 
   0jbuild/artifact/http/DefaultHttpClient$Singleton INSTANCE 2Ljbuild/artifact/http/DefaultHttpClient$Singleton;	     
httpClient Ljava/net/http/HttpClient;
      java/net/http/HttpClient 
newBuilder $()Ljava/net/http/HttpClient$Builder;	      !java/net/http/HttpClient$Redirect NORMAL #Ljava/net/http/HttpClient$Redirect;     ! "  java/net/http/HttpClient$Builder followRedirects G(Ljava/net/http/HttpClient$Redirect;)Ljava/net/http/HttpClient$Builder;       
 & ' ( ) * java/time/Duration 	ofSeconds (J)Ljava/time/Duration;  , - . connectTimeout 8(Ljava/time/Duration;)Ljava/net/http/HttpClient$Builder;  0 1 2 build ()Ljava/net/http/HttpClient; 4 &jbuild/artifact/http/DefaultHttpClient Code LineNumberTable get create 
SourceFile DefaultHttpClient.java NestMembers InnerClasses 	Singleton Builder Redirect 1 3           5        *� �    6        	 7 2  5         � � �    6        
 8 2  5   D      � � �   #� %� + � / �    6                 9    : ;      <      3 =@   >	   ?@