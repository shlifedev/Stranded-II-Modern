Global udp
Global udp_port
Global udp_ip
Global udp_host
Global udp_hostip
Global udp_hostport
Global udp_indb
Global udp_indbp
Global udp_indbs

Global udp_ms
Global udp_sendtimer
Global udp_sendrate=100
Global udp_request=100
Global udp_rerequest=500
Global udp_ack=2000
Global udp_timeout=20000


Type Tudp_con
	Field ip
	Field port
	Field iid
	Field sid
	Field ack
	Field lastaction
	Field resend
End Type

Type Tudp_ipq
	Field ip
	Field port
	Field id
	Field db
	Field reliable
	Field system
	Field checked
	Field time
End Type

Type Tudp_spq
	Field ip
	Field port
	Field id
	Field db
	Field sended
	Field reliable
	Field time
End Type

Type Tudp_msg
	Field ip
	Field port
	Field db
	Field reliable
	Field system
End Type

Type Tudp_cest
	Field ip
	Field port
	Field msg
End Type

Type Tudp_resend
	Field ip
	Field port
	Field id
	Field db
End Type