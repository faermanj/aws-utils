#!/bin/bash

#Puts all files in a directory in a S3 bucket

# Aux Functions
die () {
    echo >&2 "$@"
    exit 1
}

# Capture input
bucket=${1:-"s3inbox"}
dir=${2:-"."}
id=${3:-$AWS_ACCESS_KEY_ID}
secret=${4:-$AWS_SECRET_ACCESS_KEY}


# Validate input
#if [ ! -e ${fi} ]; then
#    die "${f} does not exist";
#fi

# if [ -z "${bucket}" ]; then
#     die "In what bucket should i put ${dir}"
# fi

if [ -z "${id}" ]; then
 	die "What is your access key id?"
fi

if [ -z "${secret}" ]; then
 	die "What is your secret access key?"
fi


function log(){
	echo $@
}

s3Date=$(curl -s --head http://s3.amazonaws.com | grep Date | cut -d: -f2- | tr '\r' ' ' | sed -e 's/^ *//g' -e 's/ *$//g')
xAmzDate="x-amz-date:$s3Date"

#Usage s3put file
function s3put(){
	file=$1
	canFileName=$(echo $file | sed 's/^\.\///g')
	log $canFileName
	# Canonical string calculation

	VERB="PUT"
	MD5=""
	TYPE="application/x-www-form-urlencoded"
	AMZ_DATE=""
	CanonicalizedAmzHeaders=$xAmzDate
	CanonicalizedResource="/${bucket}/${canFileName}"
	can="${VERB}\n${MD5}\n${TYPE}\n${AMZ_DATE}\n${CanonicalizedAmzHeaders}\n${CanonicalizedResource}"

	#Request signature
	auth=$(echo -en $can | openssl dgst -sha1 -hmac "$secret" -binary | base64)

	# GO
	log "* PUT [$canFileName][$can]"
	curl -s -X PUT  -d "@${canFileName}" -H "$xAmzDate" -H "Authorization: AWS $id:$auth" https://s3-sa-east-1.amazonaws.com/${bucket}/${canFileName}

}

pushd $dir
for file in `find . -type f` ; 
do  
	s3put $file; 
done
popd





