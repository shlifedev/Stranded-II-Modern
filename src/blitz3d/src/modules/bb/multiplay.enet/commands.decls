HostNetGame%( game_name$ ):"bbHostNetGame"
JoinNetGame%( game_name$,ip_address$ ):"bbJoinNetGame"

CreateNetPlayer.BBPlayer( name$ ):"bbCreateNetPlayer"

SendNetMsg%( type%,msg$,from_player.BBPlayer,to_player.BBPlayer=0,reliable%=1 ):"bbSendNetMsg"

RecvNetMsg%():"bbRecvNetMsg"
NetMsgType%():"bbNetMsgType"
NetMsgFrom.BBPlayer():"bbNetMsgFrom"
NetMsgTo.BBPlayer():"bbNetMsgTo"
NetMsgData$():"bbNetMsgData"
