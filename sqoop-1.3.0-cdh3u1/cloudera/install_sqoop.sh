#!/bin/sh
# Copyright 2009 Cloudera, inc.
set -ex

usage() {
  echo "
usage: $0 <options>
  Required not-so-options:
     --cloudera-source-dir=DIR   path to cloudera distribution files
     --build-dir=DIR             path to sqoopdist.dir
     --prefix=PREFIX             path to install into

  Optional options:
     --doc-dir=DIR               path to install docs into [/usr/share/doc/sqoop]
     --lib-dir=DIR               path to install sqoop home [/usr/lib/sqoop]
     --installed-lib-dir=DIR     path where lib-dir will end up on target system
     --bin-dir=DIR               path to install bins [/usr/bin]
     --examples-dir=DIR          path to install examples [doc-dir/examples]
     ... [ see source for more similar options ]
  "
  exit 1
}

OPTS=$(getopt \
  -n $0 \
  -o '' \
  -l 'cloudera-source-dir:' \
  -l 'prefix:' \
  -l 'doc-dir:' \
  -l 'lib-dir:' \
  -l 'installed-lib-dir:' \
  -l 'bin-dir:' \
  -l 'examples-dir:' \
  -l 'build-dir:' -- "$@")

if [ $? != 0 ] ; then
    usage
fi

eval set -- "$OPTS"
set -ex
while true ; do
    case "$1" in
        --cloudera-source-dir)
        CLOUDERA_SOURCE_DIR=$2 ; shift 2
        ;;
        --prefix)
        PREFIX=$2 ; shift 2
        ;;
        --build-dir)
        BUILD_DIR=$2 ; shift 2
        ;;
        --doc-dir)
        DOC_DIR=$2 ; shift 2
        ;;
        --lib-dir)
        LIB_DIR=$2 ; shift 2
        ;;
        --installed-lib-dir)
        INSTALLED_LIB_DIR=$2 ; shift 2
        ;;
        --bin-dir)
        BIN_DIR=$2 ; shift 2
        ;;
        --examples-dir)
        EXAMPLES_DIR=$2 ; shift 2
        ;;
        --)
        shift ; break
        ;;
        *)
        echo "Unknown option: $1"
        usage
        exit 1
        ;;
    esac
done

for var in CLOUDERA_SOURCE_DIR PREFIX BUILD_DIR ; do
  if [ -z "$(eval "echo \$$var")" ]; then
    echo Missing param: $var
    usage
  fi
done

DOC_DIR=${DOC_DIR:-/usr/share/doc/sqoop}
LIB_DIR=${LIB_DIR:-/usr/lib/sqoop}
BIN_DIR=${BIN_DIR:-/usr/lib/sqoop/bin}
CONF_DIR=/etc/sqoop/
CONF_DIST_DIR=/etc/sqoop/conf/
ETC_DIR=${ETC_DIR:-/etc/sqoop}
MAN_DIR=${MAN_DIR:-/usr/share/man/man1}



install -d -m 0755 ${PREFIX}/${LIB_DIR}

install -d -m 0755 ${PREFIX}/${LIB_DIR}
cp sqoop*.jar ${PREFIX}/${LIB_DIR}

install -d -m 0755 ${PREFIX}/${LIB_DIR}/lib
cp -a lib/*.jar ${PREFIX}/${LIB_DIR}/lib

#install -d -m 0755 ${PREFIX}/${LIB_DIR}/shims
#cp -a shims/*.jar ${PREFIX}/${LIB_DIR}/shims

install -d -m 0755 $PREFIX/usr/bin

install -d -m 0755 $PREFIX/${BIN_DIR}
cp bin/* $PREFIX/${BIN_DIR}

install -d -m 0755 $PREFIX/${DOC_DIR}
cp docs/*.html  $PREFIX/${DOC_DIR}
cp docs/*.css $PREFIX/${DOC_DIR}
cp -r docs/api $PREFIX/${DOC_DIR}
cp -r docs/images $PREFIX/${DOC_DIR}


install -d -m 0755 $PREFIX/$MAN_DIR
for i in sqoop sqoop-codegen sqoop-export sqoop-import-all-tables sqoop-version sqoop-create-hive-table sqoop-help sqoop-list-databases sqoop-eval sqoop-import sqoop-list-tables sqoop-job sqoop-metastore sqoop-merge
	do echo "Copying manpage $i"
	cp docs/man/$i* $PREFIX/$MAN_DIR
	echo "Creating wrapper for $i"
	wrapper=$PREFIX/usr/bin/$i
	mkdir -p `dirname $wrapper`
	cat > $wrapper <<EOF
#!/bin/sh
export HADOOP_HOME=/usr/lib/hadoop
export SQOOP_HOME=/usr/lib/sqoop
exec /usr/lib/sqoop/bin/$i "\$@"
EOF
   chmod 0755 $wrapper
done

install -d -m 0755 $PREFIX/$ETC_DIR/conf
(cd ${BUILD_DIR}/conf && tar cf - .) | (cd $PREFIX/$ETC_DIR/conf && tar xf -)
rm $PREFIX/$ETC_DIR/conf/.gitignore

unlink $PREFIX/$LIB_DIR/conf || /bin/true
ln -s /etc/sqoop/conf $PREFIX/$LIB_DIR/conf
