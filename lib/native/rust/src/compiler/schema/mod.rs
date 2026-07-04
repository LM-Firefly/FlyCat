use crate::model::{FieldBehavior, ListStyle, SchemaId};

pub const DEFAULT_NAME_SERVERS: &[&str] = &["223.5.5.5", "119.29.29.29", "8.8.4.4", "1.0.0.1"];
pub const DEFAULT_FAKE_IP_FILTER: &[&str] = &[
    "+.stun.*.*",
    "+.stun.*.*.*",
    "+.stun.*.*.*.*",
    "+.stun.*.*.*.*.*",
    "lens.l.google.com",
    "*.n.n.srv.nintendo.net",
    "+.stun.playstation.net",
    "xbox.*.*.microsoft.com",
    "*.*.xboxlive.com",
    "*.msftncsi.com",
    "*.msftconnecttest.com",
    "*.mcdn.bilivideo.cn",
    "WORKGROUP",
];
pub const DEFAULT_FAKE_IP_RANGE: &str = "28.0.0.0/8";

pub const ROOT_ORDER: &[&str] = &[
    "mode",
    "log-level",
    "ipv6",
    "find-process-mode",
    "keep-alive-interval",
    "keep-alive-idle",
    "unified-delay",
    "tcp-concurrent",
    "geodata-mode",
    "allow-lan",
    "bind-address",
    "lan-allowed-ips",
    "lan-disallowed-ips",
    "authentication",
    "skip-auth-prefixes",
    "external-controller",
    "external-controller-tls",
    "external-doh-server",
    "secret",
    "external-controller-cors",
    "profile",
    "interface-name",
    "routing-mark",
    "geosite-matcher",
    "global-client-fingerprint",
    "geo-auto-update",
    "geo-update-interval",
    "geox-url",
    "dns",
    "clash-for-android",
    "hosts",
    "sniffer",
    "port",
    "socks-port",
    "mixed-port",
    "redir-port",
    "tproxy-port",
    "tun",
    "proxies",
    "proxy-providers",
    "proxy-groups",
    "rules",
    "rule-providers",
    "sub-rules",
];
pub const DNS_ORDER: &[&str] = &[
    "enable",
    "cache-algorithm",
    "prefer-h3",
    "listen",
    "ipv6",
    "use-hosts",
    "use-system-hosts",
    "respect-rules",
    "enhanced-mode",
    "fake-ip-range",
    "fake-ip-range6",
    "fake-ip-filter-mode",
    "fake-ip-ttl",
    "ipv6-timeout",
    "cache-max-size",
    "direct-nameserver-follow-policy",
    "default-nameserver",
    "nameserver",
    "fallback",
    "proxy-server-nameserver",
    "direct-nameserver",
    "nameserver-policy",
    "proxy-server-nameserver-policy",
    "fake-ip-filter",
    "fallback-filter",
];
pub const DNS_FALLBACK_FILTER_ORDER: &[&str] =
    &["geoip", "geoip-code", "domain", "ipcidr", "geosite"];
pub const SNIFFER_ORDER: &[&str] = &[
    "enable",
    "force-dns-mapping",
    "parse-pure-ip",
    "override-destination",
    "sniff",
    "force-domain",
    "skip-domain",
    "skip-src-address",
    "skip-dst-address",
];
pub const SNIFF_ORDER: &[&str] = &["HTTP", "TLS", "QUIC"];
pub const PROTOCOL_ORDER: &[&str] = &["ports", "override-destination"];
pub const TUN_ORDER: &[&str] = &[
    "enable",
    "stack",
    "auto-route",
    "auto-redirect",
    "auto-detect-interface",
    "strict-route",
    "endpoint-independent-nat",
    "mtu",
    "gso",
    "gso-max-size",
    "disable-icmp-forwarding",
    "dns-hijack",
    "route-address",
    "route-exclude-address",
    "include-package",
    "exclude-package",
];
pub const EXTERNAL_CONTROLLER_CORS_ORDER: &[&str] = &["allow-origins", "allow-private-network"];
pub const PROFILE_ORDER: &[&str] = &["store-selected", "store-fake-ip"];
pub const GEOX_URL_ORDER: &[&str] = &["geoip", "mmdb", "geosite"];
pub const APP_ORDER: &[&str] = &["append-system-dns"];
pub const PROXY_ITEM_ORDER: &[&str] = &[
    "name",
    "type",
    "server",
    "port",
    "ip-version",
    "udp",
    "interface-name",
    "routing-mark",
    "tfo",
    "mptcp",
    "dialer-proxy",
];
pub const PROXY_GROUP_ITEM_ORDER: &[&str] = &[
    "name",
    "type",
    "proxies",
    "use",
    "url",
    "interval",
    "lazy",
    "timeout",
    "max-failed-times",
    "disable-udp",
    "interface-name",
    "routing-mark",
    "include-all",
    "include-all-proxies",
    "include-all-providers",
    "filter",
    "exclude-filter",
    "exclude-type",
    "expected-status",
    "hidden",
    "icon",
];
pub const PROVIDER_ITEM_ORDER: &[&str] = &[
    "type",
    "url",
    "path",
    "behavior",
    "interval",
    "format",
    "proxy",
    "filter",
    "exclude-filter",
    "exclude-type",
    "size-limit",
    "health-check",
];

pub fn field_behavior(schema: SchemaId, key: &str) -> Option<FieldBehavior> {
    match schema {
        SchemaId::Root => match key {
            "port"
            | "socks-port"
            | "mixed-port"
            | "redir-port"
            | "tproxy-port"
            | "allow-lan"
            | "bind-address"
            | "mode"
            | "log-level"
            | "ipv6"
            | "external-controller"
            | "external-controller-tls"
            | "external-doh-server"
            | "secret"
            | "unified-delay"
            | "geodata-mode"
            | "tcp-concurrent"
            | "find-process-mode"
            | "keep-alive-interval"
            | "keep-alive-idle"
            | "interface-name"
            | "routing-mark"
            | "geosite-matcher"
            | "global-client-fingerprint"
            | "geo-auto-update"
            | "geo-update-interval" => Some(FieldBehavior::Scalar),
            "authentication" | "skip-auth-prefixes" | "lan-allowed-ips" | "lan-disallowed-ips" => {
                Some(FieldBehavior::List(ListStyle::Plain))
            }
            "hosts" | "rule-providers" | "proxy-providers" | "sub-rules" => {
                Some(FieldBehavior::Map)
            }
            "proxies" | "proxy-groups" => Some(FieldBehavior::List(ListStyle::NamedObjects)),
            "rules" => Some(FieldBehavior::Rules),
            "dns" => Some(FieldBehavior::Object(SchemaId::Dns)),
            "external-controller-cors" => {
                Some(FieldBehavior::Object(SchemaId::ExternalControllerCors))
            }
            "profile" => Some(FieldBehavior::Object(SchemaId::Profile)),
            "tun" => Some(FieldBehavior::Object(SchemaId::Tun)),
            "sniffer" => Some(FieldBehavior::Object(SchemaId::Sniffer)),
            "geox-url" => Some(FieldBehavior::Object(SchemaId::GeoxUrl)),
            "clash-for-android" => Some(FieldBehavior::Object(SchemaId::App)),
            _ => None,
        },
        SchemaId::Dns => match key {
            "enable"
            | "cache-algorithm"
            | "prefer-h3"
            | "listen"
            | "ipv6"
            | "use-hosts"
            | "use-system-hosts"
            | "respect-rules"
            | "enhanced-mode"
            | "fake-ip-range"
            | "fake-ip-range6"
            | "fake-ip-filter-mode"
            | "fake-ip-ttl"
            | "ipv6-timeout"
            | "cache-max-size"
            | "direct-nameserver-follow-policy" => Some(FieldBehavior::Scalar),
            "nameserver"
            | "fallback"
            | "default-nameserver"
            | "proxy-server-nameserver"
            | "direct-nameserver"
            | "fake-ip-filter" => Some(FieldBehavior::List(ListStyle::Plain)),
            "nameserver-policy" | "proxy-server-nameserver-policy" => Some(FieldBehavior::Map),
            "fallback-filter" => Some(FieldBehavior::Object(SchemaId::DnsFallbackFilter)),
            _ => None,
        },
        SchemaId::DnsFallbackFilter => match key {
            "geoip" | "geoip-code" => Some(FieldBehavior::Scalar),
            "domain" | "ipcidr" | "geosite" => Some(FieldBehavior::List(ListStyle::Plain)),
            _ => None,
        },
        SchemaId::Sniffer => match key {
            "enable" | "force-dns-mapping" | "parse-pure-ip" | "override-destination" => {
                Some(FieldBehavior::Scalar)
            }
            "force-domain" | "skip-domain" | "skip-src-address" | "skip-dst-address" => {
                Some(FieldBehavior::List(ListStyle::Plain))
            }
            "sniff" => Some(FieldBehavior::Object(SchemaId::Sniff)),
            _ => None,
        },
        SchemaId::Sniff => match key {
            "HTTP" | "TLS" | "QUIC" => Some(FieldBehavior::Object(SchemaId::Protocol)),
            _ => None,
        },
        SchemaId::Protocol => match key {
            "ports" => Some(FieldBehavior::List(ListStyle::Plain)),
            "override-destination" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::Tun => match key {
            "enable"
            | "stack"
            | "auto-route"
            | "auto-detect-interface"
            | "auto-redirect"
            | "mtu"
            | "gso"
            | "gso-max-size"
            | "strict-route"
            | "disable-icmp-forwarding"
            | "endpoint-independent-nat" => Some(FieldBehavior::Scalar),
            "dns-hijack"
            | "route-address"
            | "route-exclude-address"
            | "include-package"
            | "exclude-package" => Some(FieldBehavior::List(ListStyle::Plain)),
            _ => None,
        },
        SchemaId::ExternalControllerCors => match key {
            "allow-origins" => Some(FieldBehavior::List(ListStyle::Plain)),
            "allow-private-network" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::Profile => match key {
            "store-selected" | "store-fake-ip" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::GeoxUrl => match key {
            "geoip" | "mmdb" | "geosite" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::App => match key {
            "append-system-dns" => Some(FieldBehavior::Scalar),
            _ => None,
        },
        SchemaId::ProxyItem | SchemaId::ProxyGroupItem | SchemaId::ProviderItem => None,
    }
}

pub fn ordered_keys(schema: SchemaId) -> &'static [&'static str] {
    match schema {
        SchemaId::Root => ROOT_ORDER,
        SchemaId::Dns => DNS_ORDER,
        SchemaId::DnsFallbackFilter => DNS_FALLBACK_FILTER_ORDER,
        SchemaId::Sniffer => SNIFFER_ORDER,
        SchemaId::Sniff => SNIFF_ORDER,
        SchemaId::Protocol => PROTOCOL_ORDER,
        SchemaId::Tun => TUN_ORDER,
        SchemaId::ExternalControllerCors => EXTERNAL_CONTROLLER_CORS_ORDER,
        SchemaId::Profile => PROFILE_ORDER,
        SchemaId::GeoxUrl => GEOX_URL_ORDER,
        SchemaId::App => APP_ORDER,
        SchemaId::ProxyItem => PROXY_ITEM_ORDER,
        SchemaId::ProxyGroupItem => PROXY_GROUP_ITEM_ORDER,
        SchemaId::ProviderItem => PROVIDER_ITEM_ORDER,
    }
}
