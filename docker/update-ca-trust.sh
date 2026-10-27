#!/bin/sh
# Build-time only: never starts the application or accesses league data.
set -eu

# Both application stages use Ubuntu 24.04 (Noble).
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates
dpkg --compare-versions "$(dpkg-query -W -f='${Version}' ca-certificates)" ge 20260816ubuntu0.24.04.1

# Follow https://ops.java/emudhra (updated October 24, 2026).
# Match the four certificate subjects, not distro filenames or keystore aliases.
blocked_roots='CN[[:space:]]*=[[:space:]]*(emSign Root CA - G[12]|emSign ECC Root CA - G3|AAA Certificate Services)(,|$)'
work=$(mktemp -d)
trap 'rm -rf "$work"' 0 HUP INT TERM

while IFS= read -r certificate || [ -n "$certificate" ]; do
    case "$certificate" in
        ''|\#*|\!*) printf '%s\n' "$certificate"; continue ;;
    esac
    path="/usr/share/ca-certificates/$certificate"
    if [ -f "$path" ]; then
        subject=$(openssl x509 -in "$path" -noout -subject -nameopt RFC2253)
        if printf '%s\n' "$subject" | grep -Eq "$blocked_roots"; then
            printf '!%s\n' "$certificate"
            echo "Disabling system certificate: $subject" >&2
            continue
        fi
    fi
    printf '%s\n' "$certificate"
done < /etc/ca-certificates.conf > "$work/ca-certificates.conf"
cat "$work/ca-certificates.conf" > /etc/ca-certificates.conf
update-ca-certificates --fresh

# Updating Ubuntu alone does not update Temurin's separate cacerts store.
# Parse actual aliases from keytool's English output and match only certificate owners.
keytool -J-Duser.language=en -J-Duser.country=US -list -v -cacerts -storepass changeit > "$work/java-roots"
awk -v blocked="$blocked_roots" '
    /^Alias name: / { alias = substr($0, length("Alias name: ") + 1) }
    /^Owner: / && $0 ~ blocked {
        if (alias == "") exit 1
        print alias
    }
' "$work/java-roots" > "$work/aliases"
while IFS= read -r alias; do
    echo "Removing Java certificate alias: $alias"
    keytool -delete -cacerts -storepass changeit -alias "$alias"
done < "$work/aliases"

# Verify the actual stores. 'changeit' is the standard public cacerts password.
keytool -J-Duser.language=en -J-Duser.country=US -list -v -cacerts -storepass changeit > "$work/java-roots"
openssl crl2pkcs7 -nocrl -certfile /etc/ssl/certs/ca-certificates.crt \
    | openssl pkcs7 -print_certs -noout > "$work/system-roots"
blocked_entries="^(Owner:[[:space:]]*|subject[[:space:]]*=).*${blocked_roots}"
if grep -En "$blocked_entries" "$work/java-roots" "$work/system-roots" >&2; then
    echo 'Certificate remediation failed: a prohibited root remains in the store identified above.' >&2
    exit 1
else
    result=$?
    if [ "$result" -ne 1 ]; then
        echo 'Certificate remediation failed: could not inspect the trust stores.' >&2
        exit "$result"
    fi
fi

rm -rf /var/lib/apt/lists/*
echo 'System and Java trust stores updated; prohibited roots are absent.'
