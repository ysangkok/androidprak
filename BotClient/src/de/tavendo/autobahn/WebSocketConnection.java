/******************************************************************************
 *
 *  Copyright 2011 Tavendo GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package de.tavendo.autobahn;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.SocketChannel;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

public class WebSocketConnection {

   private static final String TAG = "de.tavendo.autobahn.WebSocketConnection";

   protected Handler mMasterHandler;

   protected WebSocketReader mReader;
   protected WebSocketWriter mWriter;
   protected HandlerThread mWriterThread;

   protected SocketChannel mTransportChannel;

   private URI mWsUri;
   private String mWsScheme;
   private String mWsHost;
   private int mWsPort;
   private String mWsPath;
   private String mWsQuery;

   private WebSocketHandler mWsHandler;

   protected WebSocketOptions mOptions;

   public WebSocketConnection() {
   }

   public void sendTextMessage(String payload) {
      mWriter.forward(new WebSocketMessage.TextMessage(payload));
   }

   public void sendRawTextMessage(byte[] payload) {
      mWriter.forward(new WebSocketMessage.RawTextMessage(payload));
   }

   public void sendBinaryMessage(byte[] payload) {
      mWriter.forward(new WebSocketMessage.BinaryMessage(payload));
   }

   public boolean isConnected() {
      return mTransportChannel != null && mTransportChannel.isConnected();
   }

   private void failConnection(int code, String reason) {

      mReader.quit();
      try {
         mReader.join();
      } catch (InterruptedException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }

      //mWriterThread.getLooper().quit();
      mWriter.forward(new WebSocketMessage.Quit());
      try {
         mWriterThread.join();
      } catch (InterruptedException e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }


      try {
         mTransportChannel.close();
      } catch (IOException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      mTransportChannel = null;
      if (mWsHandler != null) {
         mWsHandler.onClose(code, reason);
         //mWsHandler = null;
      } else {
         Log.d(TAG, "could not call onClose() .. already NULL");
      }

      //Log.d(TAG, "worker threads stopped");

   }


   public void connect(String wsUri, WebSocketHandler wsHandler) throws WebSocketException {
      connect(wsUri, wsHandler, new WebSocketOptions());
   }


   public void connect(String wsUri, WebSocketHandler wsHandler, WebSocketOptions options) throws WebSocketException {

      // don't connect if already connected .. user needs to disconnect first
      //
      if (mTransportChannel != null && mTransportChannel.isConnected()) {
         throw new WebSocketException("already connected");
      }

      // parse WebSockets URI
      //
      try {
         mWsUri = new URI(wsUri);

         if (!mWsUri.getScheme().equals("ws") && !mWsUri.getScheme().equals("wss")) {
            throw new WebSocketException("unsupported scheme for WebSockets URI");
         }

         if (mWsUri.getScheme().equals("wss")) {
            throw new WebSocketException("secure WebSockets not implemented");
         }

         mWsScheme = mWsUri.getScheme();

         if (mWsUri.getPort() == -1) {
            if (mWsScheme.equals("ws")) {
               mWsPort = 80;
            } else {
               mWsPort = 443;
            }
         } else {
            mWsPort = mWsUri.getPort();
         }

         if (mWsUri.getHost() == null) {
            throw new WebSocketException("no host specified in WebSockets URI");
         } else {
            mWsHost = mWsUri.getHost();
         }

         if (mWsUri.getPath() == null || mWsUri.getPath().equals("")) {
            mWsPath = "/";
         } else {
            mWsPath = mWsUri.getPath();
         }

         if (mWsUri.getQuery() == null || mWsUri.getQuery().equals("")) {
            mWsQuery = null;
         } else {
            mWsQuery = mWsUri.getQuery();
         }

      } catch (URISyntaxException e) {

         throw new WebSocketException("invalid WebSockets URI");
      }

      mWsHandler = wsHandler;

      // make copy of options!
      mOptions = new WebSocketOptions(options);

      // connect TCP socket
      // http://developer.android.com/reference/java/nio/channels/SocketChannel.html
      //
      try {
         mTransportChannel = SocketChannel.open();

         //mTransportChannel.configureBlocking(false);
         mTransportChannel.socket().connect(new InetSocketAddress(mWsHost, mWsPort), 1000);
         //mTransportChannel.connect(new InetSocketAddress(mWsHost, mWsPort));

         mTransportChannel.socket().setSoTimeout(mOptions.getSocketReceiveTimeout());
         mTransportChannel.socket().setTcpNoDelay(mOptions.getTcpNoDelay());

         if (mTransportChannel.isConnected()) {

            //Log.d(TAG, "established TCP connection to " + mWsHost + ":" + mWsPort);

            // create WebSocket master handler
            createHandler();

            // create & start WebSocket reader
            createReader();

            // create & start WebSocket writer
            createWriter();

            // start WebSockets handshake
            WebSocketMessage.ClientHandshake hs = new WebSocketMessage.ClientHandshake(mWsHost + ":" + mWsPort);
            hs.mPath = mWsPath;
            hs.mQuery = mWsQuery;
            mWriter.forward(hs);

         } else {

            throw new WebSocketException("could not connect to WebSockets server");
         }
      } catch (IOException e) {

         throw new WebSocketException("could not connect to WebSockets server (" + e.toString() + ")");
      }

   }

   public void disconnect() {
      mWriter.forward(new WebSocketMessage.Close(1000));
   }

   /**
    * Create master message handler.
    */
   protected void createHandler() {

      mMasterHandler = new Handler() {

         public void handleMessage(Message msg) {

            if (msg.obj instanceof WebSocketMessage.TextMessage) {

               WebSocketMessage.TextMessage textMessage = (WebSocketMessage.TextMessage) msg.obj;

               //Log.d(TAG, "WebSockets Text message received (length " + textMessage.mPayload.length() + ")");

               if (mWsHandler != null) {
                  mWsHandler.onTextMessage(textMessage.mPayload);
               } else {
                  Log.d(TAG, "could not call onMessage() .. already NULL");
               }

            } else if (msg.obj instanceof WebSocketMessage.RawTextMessage) {

               WebSocketMessage.RawTextMessage rawTextMessage = (WebSocketMessage.RawTextMessage) msg.obj;

               if (mWsHandler != null) {
                  mWsHandler.onRawTextMessage(rawTextMessage.mPayload);
               } else {
                  Log.d(TAG, "could not call onMessage() .. already NULL");
               }

            } else if (msg.obj instanceof WebSocketMessage.BinaryMessage) {

               WebSocketMessage.BinaryMessage binaryMessage = (WebSocketMessage.BinaryMessage) msg.obj;

               //Log.d(TAG, "WebSockets Binary message received (length " + binaryMessage.mPayload.length + ")");

               if (mWsHandler != null) {
                  mWsHandler.onBinaryMessage(binaryMessage.mPayload);
               } else {
                  Log.d(TAG, "could not call onMessage() .. already NULL");
               }

            } else if (msg.obj instanceof WebSocketMessage.Ping) {

               WebSocketMessage.Ping ping = (WebSocketMessage.Ping) msg.obj;
               //Log.d(TAG, "WebSockets Ping received");

               // reply with Pong
               WebSocketMessage.Pong pong = new WebSocketMessage.Pong();
               pong.mPayload = ping.mPayload;
               mWriter.forward(pong);

            } else if (msg.obj instanceof WebSocketMessage.Pong) {

               //WebSocketMessage.Pong pong = (WebSocketMessage.Pong) msg.obj;
               //Log.d(TAG, "WebSockets Pong received");

            } else if (msg.obj instanceof WebSocketMessage.Close) {

               WebSocketMessage.Close close = (WebSocketMessage.Close) msg.obj;
               Log.d(TAG, "WebSockets Close received (" + close.mCode + " - " + close.mReason + ")");
               mWriter.forward(new WebSocketMessage.Close(1000));

            } else if (msg.obj instanceof WebSocketMessage.ServerHandshake) {

               //WebSocketMessage.ServerHandshake serverHandshake = (WebSocketMessage.ServerHandshake) msg.obj;
               //Log.d(TAG, "WebSockets Server handshake received");

               if (mWsHandler != null) {
                  mWsHandler.onOpen();
               } else {
                  Log.d(TAG, "could not call onOpen() .. already NULL");
               }

            } else if (msg.obj instanceof WebSocketMessage.ConnectionLost) {

               @SuppressWarnings("unused")
               WebSocketMessage.ConnectionLost connnectionLost = (WebSocketMessage.ConnectionLost) msg.obj;
               failConnection(WebSocketHandler.CLOSE_CONNECTION_LOST, "WebSockets connection lost");


            } else if (msg.obj instanceof WebSocketMessage.ProtocolViolation) {

               @SuppressWarnings("unused")
               WebSocketMessage.ProtocolViolation protocolViolation = (WebSocketMessage.ProtocolViolation) msg.obj;
               failConnection(WebSocketHandler.CLOSE_PROTOCOL_ERROR, "WebSockets protocol violation");

            } else if (msg.obj instanceof WebSocketMessage.Error) {

               WebSocketMessage.Error error = (WebSocketMessage.Error) msg.obj;
               failConnection(WebSocketHandler.CLOSE_INTERNAL_ERROR, "WebSockets internal error (" + error.mException.toString() + ")");

            } else {

               processAppMessage(msg.obj);

            }
         }
      };
   }

   protected void processAppMessage(Object message) {

   }


   /**
    * Create WebSockets background writer.
    */
   protected void createWriter() {
      mWriterThread = new HandlerThread("WebSocketWriter");
      mWriterThread.start();
      mWriter = new WebSocketWriter(mWriterThread.getLooper(), mMasterHandler, mTransportChannel, mOptions);
   }


   /**
    * Create WebSockets background reader.
    */
   protected void createReader() {
      mReader = new WebSocketReader(mMasterHandler, mTransportChannel, mOptions, "WebSocketReader");
      mReader.start();
   }

}
