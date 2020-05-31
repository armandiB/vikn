Instr([\Analysis, \Utils, \RecCopy],{ arg chainin, copybuf;
	PV_Copy(chainin, copybuf);
	chainin;
});

Instr([\Analysis, \Utils, \LocalCopy],{ arg chainin, copy;
		if(copy==1,
		{PV_Copy(chainin, LocalBuf(chainin.fftSize))}, //.fftSize fails in case of multichannel expansion. Can make a multichannel system with multiple spectrograms later if needed (or better visualization?).
		{chainin}
	);
}, [
	nil, StaticIntegerSpec(1)
]);

Instr([\Analysis, \Utils, \XFade],{ arg chainA, chainB, fade;
	PV_XFade(chainA, chainB, fade);
});

Instr([\Analysis, \Utils, \Switch_Button],{ arg chainUnpressed, chainPressed;
	Instr([\Analysis, \Utils, \XFade]).value(chainUnpressed, chainPressed, MouseButton.kr(-1,1));
});

Instr([\Analysis, \Utils, \CopyPhase],{ arg chainTo, chainFrom, copyPhase;
	if(copyPhase==1,
		{PV_CopyPhase(chainTo, chainFrom)},
		{chainTo}
	);
}, [
	nil, nil, StaticIntegerSpec(1)
]);

//PV_MagMinus or PV_MagSubstract (in Ops)